package ar.edu.ifts2.enlace.dto;

import ar.edu.ifts2.enlace.entity.Enlace;
import java.time.Instant;
import java.util.UUID;

public record EnlacePublicaResponse(UUID id, String titulo, String descripcion, String url, String categoria, Integer orden, Instant publicadaAt) {
    public static EnlacePublicaResponse from(Enlace item) {
        return new EnlacePublicaResponse(item.getId(), item.getTitulo(), item.getDescripcion(), item.getUrl(), item.getCategoria(), item.getOrden(), item.getPublicadaAt());
    }
}
