package com.dedsafio.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuración general. Se guarda en config/dedsafio.json (se crea sola la primera vez). */
public class DedsafioConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int totalDias = 20;
    /** Duración de un día del desafío (independiente del día de Minecraft). 1200 s = 20 min. */
    public int duracionDiaSegundos = 1200;
    /** Si es true, el reloj del desafío se detiene cuando no hay jugadores conectados. */
    public boolean pausarSinJugadores = true;
    /** Lo que dura la animación de la ruleta. */
    public int duracionGiroSegundos = 7;
    public int maxHistorial = 50;
    public List<CategoryConfig> categorias = categoriasPorDefecto();

    public record EventoEncontrado(CategoryConfig categoria, EventConfig evento) {
    }

    public int duracionDiaTicks() {
        return Math.max(20, duracionDiaSegundos * 20);
    }

    /** Categorías activas que tienen al menos un evento activo: son los segmentos de la ruleta. */
    public List<CategoryConfig> ruletaActiva() {
        List<CategoryConfig> out = new ArrayList<>();
        if (categorias != null) {
            for (CategoryConfig c : categorias) {
                if (c != null && c.activa && !c.eventosActivos().isEmpty()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    public EventoEncontrado buscarEvento(String id) {
        if (categorias == null || id == null) {
            return null;
        }
        for (CategoryConfig c : categorias) {
            if (c == null || c.eventos == null) {
                continue;
            }
            for (EventConfig e : c.eventos) {
                if (e != null && id.equalsIgnoreCase(e.id)) {
                    return new EventoEncontrado(c, e);
                }
            }
        }
        return null;
    }

    public List<String> idsEventos() {
        List<String> ids = new ArrayList<>();
        for (CategoryConfig c : ruletaActiva()) {
            for (EventConfig e : c.eventosActivos()) {
                ids.add(e.id);
            }
        }
        return ids;
    }

    private void normalizar() {
        totalDias = Math.max(1, totalDias);
        duracionDiaSegundos = Math.max(1, duracionDiaSegundos);
        duracionGiroSegundos = Math.max(2, duracionGiroSegundos);
        maxHistorial = Math.max(1, maxHistorial);
        if (categorias == null) {
            categorias = categoriasPorDefecto();
        }
    }

    public static DedsafioConfig load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        DedsafioConfig cfg = GSON.fromJson(json, DedsafioConfig.class);
        if (cfg == null) {
            throw new IOException("El archivo está vacío");
        }
        cfg.normalizar();
        return cfg;
    }

    /** Lee el archivo; si no existe lo crea con los valores por defecto. Nunca lanza excepción. */
    public static DedsafioConfig loadOrCreate(Path path) {
        try {
            if (Files.exists(path)) {
                return load(path);
            }
            DedsafioConfig cfg = new DedsafioConfig();
            cfg.save(path);
            return cfg;
        } catch (Exception e) {
            com.dedsafio.DedsafioMod.LOGGER.error("No se pudo leer {}; se usan los valores por defecto", path, e);
            return new DedsafioConfig();
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    private static List<CategoryConfig> categoriasPorDefecto() {
        List<CategoryConfig> l = new ArrayList<>();

        l.add(new CategoryConfig("rojo", "MOMENTO REViiL", "#E53935", 20)
                .evento("lluvia_meteoritos", "Lluvia de meteoritos", "Caen meteoritos del cielo. ¡Busca refugio!", 90)
                .evento("rayos_peligrosos", "Rayos peligrosos", "Tormenta eléctrica especial. Aléjate de lo alto.", 60)
                .evento("intercambio_posiciones", "Intercambio de posiciones", "Los jugadores cambian de lugar entre sí.", 10)
                .evento("catastrofe_temporal", "Catástrofe temporal", "El mundo se vuelve hostil durante un rato.", 120)
                .evento("contrarreloj", "Supervivencia contra reloj", "Sobrevive hasta que se acabe el tiempo.", 180));

        l.add(new CategoryConfig("naranja", "CAMBIOS DE DIFICULTAD", "#FB8C00", 20)
                .evento("zombis_trepadores", "Zombis trepadores", "Los zombis pueden trepar paredes.", 600)
                .evento("aranas_especiales", "Arañas especiales", "Las arañas tienen nuevas habilidades.", 600)
                .evento("dano_aumentado", "Daño aumentado", "Todo el daño que recibes sube.", 300)
                .evento("monstruos_agresivos", "Monstruos agresivos", "Los monstruos te detectan desde más lejos.", 300)
                .evento("reglas_alteradas", "Reglas alteradas", "Cambian las reglas de supervivencia.", 300));

        l.add(new CategoryConfig("amarillo", "NUEVAS CRIATURAS", "#FDD835", 15)
                .evento("depredadores_rapidos", "Depredadores rápidos", "Aparecen criaturas muy veloces.", 300)
                .evento("monstruos_gigantes", "Monstruos gigantes", "Aparecen monstruos de gran tamaño.", 300)
                .evento("criaturas_habilidades", "Criaturas con habilidades", "Criaturas con poderes especiales.", 300)
                .evento("variantes_raras", "Variantes raras", "Versiones raras de los monstruos conocidos.", 300)
                .evento("jefe", "Jefe", "Un jefe con ataques propios y recompensa.", 600));

        l.add(new CategoryConfig("verde", "ANUNCIOS", "#43A047", 15)
                .evento("aviso_evento", "Aviso de evento", "Se anuncia un evento próximo.", 30)
                .evento("subasta", "Subasta", "Se abre una subasta entre los participantes.", 120)
                .evento("apertura_zona", "Apertura de zona", "Se abre una nueva zona del mapa.", 60)
                .evento("reunion", "Reunión", "Todos los participantes deben reunirse.", 120)
                .evento("noticia", "Noticia importante", "Noticia importante para todos los jugadores.", 30));

        l.add(new CategoryConfig("rosa", "MOMENTO NUTRIA", "#EC407A", 15)
                .evento("juego_equipos", "Juego por equipos", "Minijuego por equipos.", 300)
                .evento("carrera_obstaculos", "Carrera de obstáculos", "Carrera con obstáculos.", 300)
                .evento("prueba_supervivencia", "Prueba de supervivencia", "Aguanta más que los demás.", 300)
                .evento("busqueda_tesoro", "Búsqueda del tesoro", "Encuentra el tesoro escondido.", 600)
                .evento("competicion_premios", "Competición con premios", "Compite por un premio.", 300));

        l.add(new CategoryConfig("cian", "MISIONES PRINCIPALES", "#00ACC1", 15)
                .evento("fabricar_objeto", "Fabricar objeto especial", "Fabrica el objeto que se pide.", 900)
                .evento("explorar_estructura", "Explorar estructura", "Encuentra y explora una estructura.", 900)
                .evento("derrotar_criatura", "Derrotar criatura", "Derrota a la criatura indicada.", 900)
                .evento("conseguir_recursos", "Conseguir recursos", "Reúne los recursos pedidos.", 900)
                .evento("sobrevivir_condicion", "Sobrevivir a una condición", "Sobrevive a condiciones especiales.", 600));

        return l;
    }
}
