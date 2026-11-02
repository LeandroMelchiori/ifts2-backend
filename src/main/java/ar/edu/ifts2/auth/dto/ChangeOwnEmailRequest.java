package ar.edu.ifts2.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ChangeOwnEmailRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 72)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String currentPassword
) {
    public ChangeOwnEmailRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "ChangeOwnEmailRequest[email=" + email + ", currentPassword=REDACTED]";
    }
}
