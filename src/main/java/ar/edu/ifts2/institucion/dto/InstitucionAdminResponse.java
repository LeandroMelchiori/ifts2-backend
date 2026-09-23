package ar.edu.ifts2.institucion.dto;

import ar.edu.ifts2.institucion.entity.Institucion;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.time.Instant;
import java.util.UUID;

public record InstitucionAdminResponse(UUID id, String nombre, String descripcion, String direccion, String email, String telefono, String horariosAtencion, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt) {
    public static InstitucionAdminResponse from(Institucion item) {
        return new InstitucionAdminResponse(item.getId(), item.getNombre(), item.getDescripcion(), item.getDireccion(), item.getEmail(), item.getTelefono(), item.getHorariosAtencion(), item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
