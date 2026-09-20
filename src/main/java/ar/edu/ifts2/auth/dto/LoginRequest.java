package ar.edu.ifts2.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es valido")
        @Size(max = 254, message = "El email admite hasta 254 caracteres")
        String email,
        @NotBlank(message = "La password es obligatoria")
        @Size(max = 72, message = "La password admite hasta 72 caracteres")
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
