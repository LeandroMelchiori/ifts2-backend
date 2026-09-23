package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;
import ar.edu.ifts2.noticia.entity.AreaContenido;
import java.time.LocalDate;

public record NoticiaAdminResponse(UUID id, String titulo, String resumen, String contenido,
                                   EstadoNoticia estado, Instant publicadaAt, Instant createdAt, Instant updatedAt,
                                   String portadaObjectKey, URI portadaUrl,
        AreaContenido area, boolean mostrarEnNovedades, String enlaceUrl, LocalDate fecha) {
    public static NoticiaAdminResponse from(Noticia noticia, URI portadaUrl) {
        return new NoticiaAdminResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(),
                noticia.getContenido(), noticia.getEstado(), noticia.getPublicadaAt(),
                noticia.getCreatedAt(), noticia.getUpdatedAt(), noticia.getPortadaObjectKey(), portadaUrl,
                noticia.getArea(), noticia.isMostrarEnNovedades(), noticia.getEnlaceUrl(), noticia.getFecha());
    }
}
