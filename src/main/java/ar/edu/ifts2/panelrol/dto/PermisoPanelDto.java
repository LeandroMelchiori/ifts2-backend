package ar.edu.ifts2.panelrol.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record PermisoPanelDto(@NotNull Boolean view, @NotNull Boolean manage) {
    @AssertTrue(message = "Gestionar requiere permiso Ver")
    @JsonIgnore
    public boolean isConsistent() { return !Boolean.TRUE.equals(manage) || Boolean.TRUE.equals(view); }
}
