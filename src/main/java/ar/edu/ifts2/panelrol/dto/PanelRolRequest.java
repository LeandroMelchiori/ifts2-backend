package ar.edu.ifts2.panelrol.dto;

import ar.edu.ifts2.panelrol.entity.SeccionPanel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.Map;

public record PanelRolRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(max = 4)
        @Schema(description = "Solo destacados, informacion, contenido e instagram. Faltantes se reemplazan por false/false; gestionar requiere ver.")
        Map<@NotNull String, @NotNull @Valid PermisoPanelDto> permissions,
        @NotBlank @Size(max = 72)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String currentPassword
) {
    public PanelRolRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    @AssertTrue(message = "Seccion del panel desconocida")
    @JsonIgnore
    public boolean isKnownSections() {
        return permissions == null || permissions.keySet().stream().allMatch(SeccionPanel::isKnown);
    }
    @Override public String toString() { return "PanelRolRequest[REDACTED]"; }
}
