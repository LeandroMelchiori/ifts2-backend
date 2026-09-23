package ar.edu.ifts2.usuario.dto;

import ar.edu.ifts2.usuario.entity.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record ActualizarUsuarioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull Rol rol,
        @NotNull Boolean activo
) {
    public ActualizarUsuarioRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
