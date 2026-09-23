package ar.edu.ifts2.usuario.dto;

import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(UUID id, String nombre, String apellido, String email,
                              Rol rol, boolean activo, Instant createdAt, Instant updatedAt) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getEmail(), usuario.getRol(), usuario.isActivo(), usuario.getCreatedAt(), usuario.getUpdatedAt());
    }
}
