package ar.edu.ifts2.meta.dto;

import jakarta.validation.constraints.NotNull;

public record MetaVisibilidadRequest(@NotNull Boolean visible) { }
