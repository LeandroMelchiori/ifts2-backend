package ar.edu.ifts2.auth.dto;

import ar.edu.ifts2.usuario.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank
        @Size(max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String currentPassword,

        @ValidPassword
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, minLength = 12,
                maxLength = 72, description = "Minimo 12 caracteres y maximo 72 bytes UTF-8")
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangeOwnPasswordRequest[REDACTED]";
    }
}
