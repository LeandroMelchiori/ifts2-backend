package ar.edu.ifts2.auth.dto;

import ar.edu.ifts2.usuario.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank @Size(max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String currentPassword,
        @ValidPassword
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password", minLength = 12,
                description = "Minimo 12 caracteres y maximo 72 bytes UTF-8") String newPassword
) {
    @Override public String toString() { return "ChangeOwnPasswordRequest[REDACTED]"; }
}
