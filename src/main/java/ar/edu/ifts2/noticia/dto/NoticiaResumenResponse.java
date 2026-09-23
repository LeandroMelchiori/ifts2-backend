package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.net.URI;
import java.util.UUID;

public record NoticiaResumenResponse(UUID id, String titulo, String resumen, Instant publicadaAt, URI portadaUrl) {
    public static NoticiaResumenResponse from(Noticia noticia, URI portadaUrl) {
        return new NoticiaResumenResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(), noticia.getPublicadaAt(), portadaUrl);
    }
}
