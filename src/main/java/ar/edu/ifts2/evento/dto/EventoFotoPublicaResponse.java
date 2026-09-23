package ar.edu.ifts2.evento.dto;

import ar.edu.ifts2.evento.entity.EventoFoto;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record EventoFotoPublicaResponse(UUID id, UUID eventoId, String eventoTitulo, Instant fechaInicio,
                                       String etiqueta, int orden, URI imagenUrl) {
    public static EventoFotoPublicaResponse from(EventoFoto foto, URI url) {
        return new EventoFotoPublicaResponse(foto.getId(), foto.getEvento().getId(), foto.getEvento().getTitulo(),
                foto.getEvento().getFechaInicio(), foto.getEtiqueta(), foto.getOrden(), url);
    }
}
