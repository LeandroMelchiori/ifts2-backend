package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.util.UUID;

public record NoticiaAdminResponse(UUID id, String titulo, String resumen, String contenido,
                                   EstadoNoticia estado, Instant publicadaAt, Instant createdAt, Instant updatedAt) {
    public static NoticiaAdminResponse from(Noticia noticia) {
        return new NoticiaAdminResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(),
                noticia.getContenido(), noticia.getEstado(), noticia.getPublicadaAt(),
                noticia.getCreatedAt(), noticia.getUpdatedAt());
    }
}
