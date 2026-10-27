package com.dedsafio.challenge;

import java.util.ArrayList;
import java.util.List;

/** Estado guardado del desafío (se escribe en dedsafio_state.json dentro del mundo). */
public class ChallengeState {
    public enum Estado {
        DETENIDO, EN_CURSO, PAUSADO, FINALIZADO
    }

    public Estado estado = Estado.DETENIDO;
    public int dia = 1;
    public int ticksDelDia = 0;
    public EventoActivo eventoActivo = null;
    public List<HistorialEntrada> historial = new ArrayList<>();

    public static class EventoActivo {
        public String categoriaId;
        public String eventoId;
        public String nombre;
        public int ticksRestantes;
    }

    public static class HistorialEntrada {
        public int dia;
        public String categoriaId;
        public String categoriaNombre;
        public String eventoId;
        public String eventoNombre;
        public long fechaMillis;
    }
}
