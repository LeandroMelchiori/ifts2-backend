package ar.edu.ifts2.usuario.dto;

import ar.edu.ifts2.usuario.entity.Rol;
import ar.edu.ifts2.usuario.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CrearUsuarioRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 254) String email,
        @ValidPassword
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, minLength = 12,
                maxLength = 72, description = "Minimo 12 caracteres y maximo 72 bytes UTF-8")
        String password,
        @NotNull Rol rol
) {
    public CrearUsuarioRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "CrearUsuarioRequest[REDACTED]"; }
}
