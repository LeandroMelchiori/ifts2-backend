package ar.edu.ifts2.evento.dto;

import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record EventoAdminResponse(UUID id, String titulo, String resumen, String descripcion, Instant fechaInicio, Instant fechaFin, String lugar, URI portadaUrl, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt, String portadaObjectKey) {
    public static EventoAdminResponse from(Evento item, URI url) {
        return new EventoAdminResponse(item.getId(), item.getTitulo(), item.getResumen(), item.getDescripcion(), item.getFechaInicio(), item.getFechaFin(), item.getLugar(), url, item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt(), item.getPortadaObjectKey());
    }
}
