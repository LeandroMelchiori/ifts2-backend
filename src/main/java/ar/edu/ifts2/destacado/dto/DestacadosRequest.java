package ar.edu.ifts2.destacado.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record DestacadosRequest(@NotNull @Size(max = 6) List<@NotNull @Valid Referencia> items) {
    public record Referencia(UUID noticiaId, UUID eventoId) {
        @JsonIgnore
        @AssertTrue(message = "Indicar exactamente una noticia o un evento")
        public boolean isOrigenValido() {
            return (noticiaId != null) != (eventoId != null);
        }
    }
}
