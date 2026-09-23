package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;

public record NoticiaAdminResponse(UUID id, String titulo, String resumen, String contenido,
                                   EstadoNoticia estado, Instant publicadaAt, Instant createdAt, Instant updatedAt,
                                   String portadaObjectKey, URI portadaUrl) {
    public static NoticiaAdminResponse from(Noticia noticia, URI portadaUrl) {
        return new NoticiaAdminResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(),
                noticia.getContenido(), noticia.getEstado(), noticia.getPublicadaAt(),
                noticia.getCreatedAt(), noticia.getUpdatedAt(), noticia.getPortadaObjectKey(), portadaUrl);
    }
}
