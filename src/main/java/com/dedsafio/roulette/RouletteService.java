package com.dedsafio.roulette;

import com.dedsafio.DedsafioMod;
import com.dedsafio.challenge.ChallengeManager;
import com.dedsafio.challenge.ChallengeState;
import com.dedsafio.config.CategoryConfig;
import com.dedsafio.config.DedsafioConfig;
import com.dedsafio.config.EventConfig;
import com.dedsafio.net.RouletteSpinPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * La ruleta. El servidor elige el resultado (categoría por peso, luego evento al azar),
 * se lo manda a los clientes para que lo animen, y cuando termina la animación activa el evento.
 */
public final class RouletteService {

    private static final class Pending {
        final CategoryConfig categoria;
        final EventConfig evento;
        int ticksRestantes;

        Pending(CategoryConfig categoria, EventConfig evento, int ticksRestantes) {
            this.categoria = categoria;
            this.evento = evento;
            this.ticksRestantes = ticksRestantes;
        }
    }

    private final Random random = new Random();
    private Pending pending;

    public boolean estaGirando() {
        return pending != null;
    }

    /** Devuelve un mensaje de error, o null si la ruleta empezó a girar. */
    public String girar(MinecraftServer server, ChallengeManager mgr) {
        ChallengeState state = mgr.state();
        DedsafioConfig cfg = mgr.config();

        if (state.estado != ChallengeState.Estado.EN_CURSO) {
            return "El desafío no está en curso. Usa /dedsafio iniciar.";
        }
        if (pending != null) {
            return "La ruleta ya está girando.";
        }
        if (state.eventoActivo != null) {
            return "Ya hay un evento activo: " + state.eventoActivo.nombre
                    + ". Espera a que termine o usa /dedsafio cancelar.";
        }

        List<CategoryConfig> rueda = cfg.ruletaActiva();
        if (rueda.isEmpty()) {
            return "No hay categorías activas con eventos en la configuración.";
        }

        int ganador = elegirIndice(rueda);
        CategoryConfig cat = rueda.get(ganador);
        List<EventConfig> posibles = cat.eventosActivos();
        EventConfig ev = posibles.get(random.nextInt(posibles.size()));

        List<RouletteSpinPayload.Segment> segmentos = new ArrayList<>();
        for (CategoryConfig c : rueda) {
            segmentos.add(new RouletteSpinPayload.Segment(c.nombre, c.rgb()));
        }

        int spinTicks = Math.max(40, cfg.duracionGiroSegundos * 20);
        float offset = (random.nextFloat() - 0.5f) * 0.8f; // dónde cae dentro del segmento

        RouletteSpinPayload payload = new RouletteSpinPayload(
                false, segmentos, ganador, offset, spinTicks,
                cat.nombre, cat.rgb(), ev.nombre, ev.descripcion == null ? "" : ev.descripcion,
                ev.duracionSegundos);

        pending = new Pending(cat, ev, spinTicks + 10);
        enviarATodos(server, payload);
        mgr.broadcast(server, Text.literal("La ruleta está girando...").formatted(Formatting.GOLD, Formatting.BOLD));
        return null;
    }

    public void tick(MinecraftServer server, ChallengeManager mgr) {
        if (pending == null) {
            return;
        }
        if (--pending.ticksRestantes > 0) {
            return;
        }
        Pending p = pending;
        pending = null;
        String error = mgr.activarEvento(server, p.categoria, p.evento);
        if (error != null) {
            mgr.broadcast(server, Text.literal("La ruleta no pudo activar el evento: " + error).formatted(Formatting.RED));
        }
    }

    /** Cancela una ruleta en curso. Devuelve true si había una. */
    public boolean cancelar(MinecraftServer server) {
        if (pending == null) {
            return false;
        }
        pending = null;
        enviarATodos(server, RouletteSpinPayload.cancelado());
        return true;
    }

    private int elegirIndice(List<CategoryConfig> rueda) {
        int total = 0;
        for (CategoryConfig c : rueda) {
            total += Math.max(0, c.peso);
        }
        if (total <= 0) {
            return random.nextInt(rueda.size());
        }
        int tirada = random.nextInt(total);
        for (int i = 0; i < rueda.size(); i++) {
            tirada -= Math.max(0, rueda.get(i).peso);
            if (tirada < 0) {
                return i;
            }
        }
        return rueda.size() - 1;
    }

    private void enviarATodos(MinecraftServer server, RouletteSpinPayload payload) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, RouletteSpinPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            } else {
                DedsafioMod.LOGGER.debug("{} no tiene el mod; no ve la animación de la ruleta", player.getName().getString());
            }
        }
    }
}
