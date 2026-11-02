package ar.edu.ifts2.auth.dto;

import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.entity.Usuario;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String nombre,
        String apellido,
        String email,
        Rol rol,
        boolean activo
) {
    public static MeResponse from(Usuario usuario) {
        return new MeResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.isActivo()
        );
    }
}
