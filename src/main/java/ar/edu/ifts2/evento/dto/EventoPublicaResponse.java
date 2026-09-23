package ar.edu.ifts2.evento.dto;

import ar.edu.ifts2.evento.entity.Evento;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record EventoPublicaResponse(UUID id, String titulo, String resumen, String descripcion, Instant fechaInicio, Instant fechaFin, String lugar, URI portadaUrl, Instant publicadaAt) {
    public static EventoPublicaResponse from(Evento item, URI url) {
        return new EventoPublicaResponse(item.getId(), item.getTitulo(), item.getResumen(), item.getDescripcion(), item.getFechaInicio(), item.getFechaFin(), item.getLugar(), url, item.getPublicadaAt());
    }
}
