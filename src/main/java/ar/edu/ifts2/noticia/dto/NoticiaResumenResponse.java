package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;
import ar.edu.ifts2.noticia.entity.AreaContenido;
import java.time.LocalDate;

public record NoticiaResumenResponse(UUID id, String titulo, String resumen, Instant publicadaAt, URI portadaUrl,
        AreaContenido area, boolean mostrarEnNovedades, String enlaceUrl, LocalDate fecha) {
    public static NoticiaResumenResponse from(Noticia noticia, URI portadaUrl) {
        return new NoticiaResumenResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(), noticia.getPublicadaAt(), portadaUrl,
                noticia.getArea(), noticia.isMostrarEnNovedades(), noticia.getEnlaceUrl(), noticia.getFecha());
    }
}
