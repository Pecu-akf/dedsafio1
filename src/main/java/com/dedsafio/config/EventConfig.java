package com.dedsafio.config;

/** Un evento que puede salir en la ruleta. Todo se edita en config/dedsafio.json. */
public class EventConfig {
    public String id;
    public String nombre;
    public String descripcion;
    public int duracionSegundos = 60;
    public boolean activo = true;

    public EventConfig() {
    }

    public EventConfig(String id, String nombre, String descripcion, int duracionSegundos) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.duracionSegundos = duracionSegundos;
    }
}
