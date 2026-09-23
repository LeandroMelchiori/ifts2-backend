package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;

public record NoticiaPublicaResponse(UUID id, String titulo, String resumen, String contenido, Instant publicadaAt, URI portadaUrl) {
    public static NoticiaPublicaResponse from(Noticia noticia, URI portadaUrl) {
        return new NoticiaPublicaResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(),
                noticia.getContenido(), noticia.getPublicadaAt(), portadaUrl);
    }
}
