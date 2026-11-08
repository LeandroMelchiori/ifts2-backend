package ar.edu.ifts2.panelrol.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EliminarPanelRolRequest(
        @NotBlank @Size(max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String currentPassword
) {
    @Override public String toString() { return "EliminarPanelRolRequest[REDACTED]"; }
}
