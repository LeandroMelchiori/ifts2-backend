package ar.edu.ifts2.evento.dto;

import jakarta.validation.constraints.*;

public record EventoFotoRequest(@NotBlank @Size(max = 200) String etiqueta,
                                @NotNull @Min(0) @Max(10000) Integer orden) { }
