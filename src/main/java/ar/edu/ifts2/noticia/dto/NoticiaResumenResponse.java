package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.Noticia;
import java.time.Instant;
import java.util.UUID;

public record NoticiaResumenResponse(UUID id, String titulo, String resumen, Instant publicadaAt) {
    public static NoticiaResumenResponse from(Noticia noticia) {
        return new NoticiaResumenResponse(noticia.getId(), noticia.getTitulo(), noticia.getResumen(), noticia.getPublicadaAt());
    }
}
