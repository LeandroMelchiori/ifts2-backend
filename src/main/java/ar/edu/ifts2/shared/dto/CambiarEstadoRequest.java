package ar.edu.ifts2.shared.dto;

import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoPublicacion estado) { }
