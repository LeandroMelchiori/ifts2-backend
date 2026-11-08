package ar.edu.ifts2.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPasswordRequest(
        @NotBlank @Size(max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String password
) {
    @Override public String toString() { return "VerifyPasswordRequest[REDACTED]"; }
}
