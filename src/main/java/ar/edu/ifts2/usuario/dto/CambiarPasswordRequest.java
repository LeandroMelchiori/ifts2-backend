package ar.edu.ifts2.usuario.dto;

import ar.edu.ifts2.usuario.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;

public record CambiarPasswordRequest(
        @ValidPassword
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, minLength = 12,
                maxLength = 72, description = "Minimo 12 caracteres y maximo 72 bytes UTF-8")
        String password
) {
    @Override
    public String toString() { return "CambiarPasswordRequest[REDACTED]"; }
}
