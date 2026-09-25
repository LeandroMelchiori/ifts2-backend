package ar.edu.ifts2.destacado.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record DestacadosRequest(@NotNull @Size(max = 6) List<@NotNull @Valid Referencia> items) {
    public record Referencia(UUID noticiaId, UUID eventoId, @Pattern(regexp = "[0-9]{1,40}") String metaPostId) {
        public Referencia(UUID noticiaId, UUID eventoId) { this(noticiaId, eventoId, null); }
        @JsonIgnore
        @AssertTrue(message = "Indicar exactamente una noticia, un evento o un post Meta")
        public boolean isOrigenValido() {
            return (noticiaId != null ? 1 : 0) + (eventoId != null ? 1 : 0) + (metaPostId != null ? 1 : 0) == 1;
        }
    }
}
