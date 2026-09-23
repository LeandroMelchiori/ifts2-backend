package ar.edu.ifts2.enlace.dto;

import ar.edu.ifts2.enlace.entity.Enlace;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.time.Instant;
import java.util.UUID;

public record EnlaceAdminResponse(UUID id, String titulo, String descripcion, String url, String categoria, Integer orden, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt) {
    public static EnlaceAdminResponse from(Enlace item) {
        return new EnlaceAdminResponse(item.getId(), item.getTitulo(), item.getDescripcion(), item.getUrl(), item.getCategoria(), item.getOrden(), item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
