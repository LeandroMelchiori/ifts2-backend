package ar.edu.ifts2.autoridad.dto;

import ar.edu.ifts2.autoridad.entity.Autoridad;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record AutoridadAdminResponse(UUID id, String nombre, String apellido, String cargo, String descripcion, Integer orden, URI fotoUrl, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt, String fotoObjectKey) {
    public static AutoridadAdminResponse from(Autoridad item, URI url) {
        return new AutoridadAdminResponse(item.getId(), item.getNombre(), item.getApellido(), item.getCargo(), item.getDescripcion(), item.getOrden(), url, item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt(), item.getFotoObjectKey());
    }
}
