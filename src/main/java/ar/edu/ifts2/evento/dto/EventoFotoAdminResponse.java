package ar.edu.ifts2.evento.dto;

import ar.edu.ifts2.evento.entity.EventoFoto;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record EventoFotoAdminResponse(UUID id, UUID eventoId, String eventoTitulo, Instant fechaInicio,
        EstadoPublicacion estado, String etiqueta, int orden, URI imagenUrl, String objectKey,
        Instant createdAt, Instant updatedAt) {
    public static EventoFotoAdminResponse from(EventoFoto foto, URI url) {
        return new EventoFotoAdminResponse(foto.getId(), foto.getEvento().getId(), foto.getEvento().getTitulo(),
                foto.getEvento().getFechaInicio(), foto.getEvento().getEstado(), foto.getEtiqueta(), foto.getOrden(),
                url, foto.getObjectKey(), foto.getCreatedAt(), foto.getUpdatedAt());
    }
}
