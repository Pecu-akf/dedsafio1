package com.dedsafio.config;

import java.util.ArrayList;
import java.util.List;

/** Una categoría (color) de la ruleta. */
public class CategoryConfig {
    public String id;
    public String nombre;
    /** Color en formato #RRGGBB. */
    public String color = "#FFFFFF";
    /** Probabilidad relativa de que salga esta categoría. */
    public int peso = 10;
    public boolean activa = true;
    public List<EventConfig> eventos = new ArrayList<>();

    public CategoryConfig() {
    }

    public CategoryConfig(String id, String nombre, String color, int peso) {
        this.id = id;
        this.nombre = nombre;
        this.color = color;
        this.peso = peso;
    }

    public CategoryConfig evento(String id, String nombre, String descripcion, int duracionSegundos) {
        eventos.add(new EventConfig(id, nombre, descripcion, duracionSegundos));
        return this;
    }

    /** Color como entero RGB (sin alfa). Si el texto es inválido devuelve blanco. */
    public int rgb() {
        try {
            String hex = color.startsWith("#") ? color.substring(1) : color;
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0xFFFFFF;
        }
    }

    public List<EventConfig> eventosActivos() {
        List<EventConfig> out = new ArrayList<>();
        if (eventos != null) {
            for (EventConfig e : eventos) {
                if (e != null && e.activo && e.id != null) {
                    out.add(e);
                }
            }
        }
        return out;
    }
}
