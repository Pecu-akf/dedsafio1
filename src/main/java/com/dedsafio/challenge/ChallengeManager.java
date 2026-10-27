package com.dedsafio.challenge;

import com.dedsafio.DedsafioMod;
import com.dedsafio.challenge.ChallengeState.Estado;
import com.dedsafio.config.CategoryConfig;
import com.dedsafio.config.DedsafioConfig;
import com.dedsafio.config.EventConfig;
import com.dedsafio.roulette.RouletteService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Corazón del mod: días, estado del desafío, eventos activos, historial y guardado.
 * Todo lo que cambia el estado pasa por aquí y solo corre en el hilo del servidor.
 */
public final class ChallengeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DedsafioConfig config;
    private final Path configPath;
    private final RouletteService roulette = new RouletteService();

    private ChallengeState state = new ChallengeState();
    private Path statePath;
    private int saveTimer;
    private boolean dirty;

    public ChallengeManager(DedsafioConfig config, Path configPath) {
        this.config = config;
        this.configPath = configPath;
    }

    public DedsafioConfig config() {
        return config;
    }

    public ChallengeState state() {
        return state;
    }

    public RouletteService roulette() {
        return roulette;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void onServerStarted(MinecraftServer server) {
        statePath = server.getSavePath(WorldSavePath.ROOT).resolve("dedsafio_state.json");
        loadState();
    }

    public void onServerStopping(MinecraftServer server) {
        roulette.cancelar(server);
        saveState();
    }

    // ------------------------------------------------------------------ control del desafío

    /** Todos los métodos de acción devuelven un mensaje de error, o null si salió bien. */
    public String iniciar(MinecraftServer server) {
        if (state.estado == Estado.EN_CURSO || state.estado == Estado.PAUSADO) {
            return "El desafío ya está en marcha. Usa /dedsafio finalizar primero.";
        }
        roulette.cancelar(server);
        state = new ChallengeState();
        state.estado = Estado.EN_CURSO;
        saveState();

        broadcast(server, Text.literal("¡Comienza el DEDsafío! Día 1/" + config.totalDias)
                .formatted(Formatting.GOLD, Formatting.BOLD));
        titulo(server, Text.literal("DEDsafío").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal("Día 1 de " + config.totalDias).formatted(Formatting.WHITE));
        return null;
    }

    public String pausar(MinecraftServer server) {
        if (state.estado != Estado.EN_CURSO) {
            return "El desafío no está en curso.";
        }
        state.estado = Estado.PAUSADO;
        saveState();
        broadcast(server, Text.literal("El desafío está en pausa.").formatted(Formatting.YELLOW));
        return null;
    }

    public String reanudar(MinecraftServer server) {
        if (state.estado != Estado.PAUSADO) {
            return "El desafío no está pausado.";
        }
        state.estado = Estado.EN_CURSO;
        saveState();
        broadcast(server, Text.literal("El desafío continúa.").formatted(Formatting.GREEN));
        return null;
    }

    public String finalizar(MinecraftServer server) {
        if (state.estado == Estado.DETENIDO || state.estado == Estado.FINALIZADO) {
            return "No hay un desafío activo.";
        }
        terminar(server, "El desafío ha sido finalizado por un administrador.");
        return null;
    }

    public String fijarDia(MinecraftServer server, int dia) {
        if (state.estado != Estado.EN_CURSO && state.estado != Estado.PAUSADO) {
            return "No hay un desafío activo.";
        }
        if (dia < 1 || dia > config.totalDias) {
            return "El día debe estar entre 1 y " + config.totalDias + ".";
        }
        state.dia = dia;
        state.ticksDelDia = 0;
        saveState();
        broadcast(server, Text.literal("Día " + dia + "/" + config.totalDias).formatted(Formatting.GOLD, Formatting.BOLD));
        return null;
    }

    public String cambiarDias(MinecraftServer server, int delta) {
        return fijarDia(server, state.dia + delta);
    }

    /** Cancela la ruleta que esté girando y/o el evento activo. */
    public String cancelar(MinecraftServer server) {
        boolean algo = false;
        if (roulette.cancelar(server)) {
            broadcast(server, Text.literal("La ruleta fue cancelada por un administrador.").formatted(Formatting.YELLOW));
            algo = true;
        }
        if (state.eventoActivo != null) {
            finalizarEventoActivo(server, true);
            algo = true;
        }
        return algo ? null : "No hay ninguna ruleta girando ni evento activo.";
    }

    /** Vuelve a leer config/dedsafio.json. Si tiene errores se queda con la configuración anterior. */
    public String recargarConfig() {
        try {
            config = DedsafioConfig.load(configPath);
            state.dia = Math.min(state.dia, config.totalDias);
            return null;
        } catch (Exception e) {
            DedsafioMod.LOGGER.error("Error al recargar la configuración", e);
            return "No se pudo leer dedsafio.json: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------ eventos

    /** Activa un evento ya elegido (por la ruleta o por comando). */
    public String activarEvento(MinecraftServer server, CategoryConfig cat, EventConfig ev) {
        if (state.estado != Estado.EN_CURSO) {
            return "El desafío no está en curso.";
        }
        if (state.eventoActivo != null) {
            return "Ya hay un evento activo: " + state.eventoActivo.nombre + ".";
        }

        ChallengeState.EventoActivo activo = new ChallengeState.EventoActivo();
        activo.categoriaId = cat.id;
        activo.eventoId = ev.id;
        activo.nombre = ev.nombre;
        activo.ticksRestantes = Math.max(20, ev.duracionSegundos * 20);
        state.eventoActivo = activo;

        ChallengeState.HistorialEntrada h = new ChallengeState.HistorialEntrada();
        h.dia = state.dia;
        h.categoriaId = cat.id;
        h.categoriaNombre = cat.nombre;
        h.eventoId = ev.id;
        h.eventoNombre = ev.nombre;
        h.fechaMillis = System.currentTimeMillis();
        state.historial.add(h);
        while (state.historial.size() > config.maxHistorial) {
            state.historial.remove(0);
        }
        saveState();

        int color = cat.rgb();
        broadcast(server, Text.literal("=== " + cat.nombre + " ===").styled(s -> s.withColor(color).withBold(true)));
        broadcast(server, Text.literal(ev.nombre + "  (" + fmt(activo.ticksRestantes) + ")").formatted(Formatting.WHITE, Formatting.BOLD));
        if (ev.descripcion != null && !ev.descripcion.isEmpty()) {
            broadcast(server, Text.literal(ev.descripcion).formatted(Formatting.GRAY));
        }
        titulo(server, Text.literal(ev.nombre).styled(s -> s.withColor(color).withBold(true)),
                Text.literal(cat.nombre).formatted(Formatting.WHITE));
        return null;
    }

    public String activarEventoPorId(MinecraftServer server, String id) {
        DedsafioConfig.EventoEncontrado found = config.buscarEvento(id);
        if (found == null) {
            return "No existe ningún evento con id '" + id + "'.";
        }
        if (!found.evento().activo || !found.categoria().activa) {
            return "Ese evento está desactivado en la configuración.";
        }
        if (roulette.estaGirando()) {
            return "La ruleta está girando; espera a que termine o usa /dedsafio cancelar.";
        }
        return activarEvento(server, found.categoria(), found.evento());
    }

    private void finalizarEventoActivo(MinecraftServer server, boolean cancelado) {
        ChallengeState.EventoActivo ev = state.eventoActivo;
        if (ev == null) {
            return;
        }
        state.eventoActivo = null;
        saveState();
        if (cancelado) {
            broadcast(server, Text.literal("Evento cancelado: " + ev.nombre).formatted(Formatting.YELLOW));
        } else {
            broadcast(server, Text.literal("Terminó el evento: " + ev.nombre).formatted(Formatting.GREEN));
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftServer server) {
        boolean enCurso = state.estado == Estado.EN_CURSO;

        if (enCurso && (!config.pausarSinJugadores || !server.getPlayerManager().getPlayerList().isEmpty())) {
            state.ticksDelDia++;

            ChallengeState.EventoActivo ev = state.eventoActivo;
            if (ev != null && --ev.ticksRestantes <= 0) {
                finalizarEventoActivo(server, false);
            }
            if (state.ticksDelDia >= config.duracionDiaTicks()) {
                avanzarDia(server);
            }
            dirty = true;
        }

        roulette.tick(server, this);

        if (server.getTicks() % 20 == 0
                && (state.estado == Estado.EN_CURSO || state.estado == Estado.PAUSADO)) {
            Text barra = construirBarra();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(barra, true);
            }
        }

        if (++saveTimer >= 600) {
            saveTimer = 0;
            if (dirty) {
                saveState();
            }
        }
    }

    private void avanzarDia(MinecraftServer server) {
        if (state.dia >= config.totalDias) {
            terminar(server, "¡El desafío ha terminado! Se completaron los " + config.totalDias + " días.");
            return;
        }
        state.dia++;
        state.ticksDelDia = 0;
        saveState();
        broadcast(server, Text.literal("Amanece el día " + state.dia + "/" + config.totalDias)
                .formatted(Formatting.GOLD, Formatting.BOLD));
        titulo(server, Text.literal("Día " + state.dia).formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal("de " + config.totalDias).formatted(Formatting.WHITE));
    }

    private void terminar(MinecraftServer server, String mensaje) {
        roulette.cancelar(server);
        state.eventoActivo = null;
        state.estado = Estado.FINALIZADO;
        saveState();
        broadcast(server, Text.literal(mensaje).formatted(Formatting.GOLD, Formatting.BOLD));
        titulo(server, Text.literal("DEDsafío").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal("Fin del desafío").formatted(Formatting.WHITE));
    }

    // ------------------------------------------------------------------ textos

    public String describirDia() {
        if (state.estado == Estado.DETENIDO) {
            return "El desafío no ha empezado.";
        }
        int restantes = Math.max(0, config.duracionDiaTicks() - state.ticksDelDia);
        return "Día " + state.dia + " de " + config.totalDias + " (" + state.estado
                + "). Faltan " + fmt(restantes) + " para el siguiente día.";
    }

    public String resumenEstado() {
        String evento = state.eventoActivo == null
                ? "ninguno"
                : state.eventoActivo.nombre + " (" + fmt(state.eventoActivo.ticksRestantes) + ")";
        return describirDia() + " Evento activo: " + evento
                + ". Ruleta: " + (roulette.estaGirando() ? "girando" : "libre") + ".";
    }

    private Text construirBarra() {
        MutableText t = Text.literal("DEDsafío").formatted(Formatting.GOLD, Formatting.BOLD);
        t.append(Text.literal("  Día " + state.dia + "/" + config.totalDias).formatted(Formatting.WHITE));
        if (state.estado == Estado.PAUSADO) {
            t.append(Text.literal("  (PAUSADO)").formatted(Formatting.YELLOW));
        } else {
            int restantes = Math.max(0, config.duracionDiaTicks() - state.ticksDelDia);
            t.append(Text.literal("  Siguiente día en " + fmt(restantes)).formatted(Formatting.GRAY));
        }
        if (state.eventoActivo != null) {
            t.append(Text.literal("  Evento: " + state.eventoActivo.nombre
                    + " (" + fmt(state.eventoActivo.ticksRestantes) + ")").formatted(Formatting.AQUA));
        }
        return t;
    }

    public static String fmt(int ticks) {
        int s = Math.max(0, ticks / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(text, false);
    }

    private void titulo(MinecraftServer server, Text title, Text subtitle) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }

    // ------------------------------------------------------------------ guardado

    private void loadState() {
        state = new ChallengeState();
        if (statePath == null || !Files.exists(statePath)) {
            return;
        }
        try {
            String json = Files.readString(statePath, StandardCharsets.UTF_8);
            ChallengeState loaded = GSON.fromJson(json, ChallengeState.class);
            if (loaded != null) {
                if (loaded.estado == null) {
                    loaded.estado = Estado.DETENIDO;
                }
                if (loaded.historial == null) {
                    loaded.historial = new java.util.ArrayList<>();
                }
                loaded.dia = Math.max(1, Math.min(loaded.dia, config.totalDias));
                state = loaded;
                DedsafioMod.LOGGER.info("Estado cargado: {}", describirDia());
            }
        } catch (Exception e) {
            DedsafioMod.LOGGER.error("No se pudo leer {}; se empieza con un estado vacío", statePath, e);
        }
    }

    public void saveState() {
        dirty = false;
        if (statePath == null) {
            return;
        }
        try {
            Path tmp = statePath.resolveSibling(statePath.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, statePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DedsafioMod.LOGGER.error("No se pudo guardar el estado del desafío", e);
        }
    }
}
