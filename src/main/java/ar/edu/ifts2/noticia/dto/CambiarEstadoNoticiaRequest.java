package ar.edu.ifts2.noticia.dto;

import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoNoticiaRequest(@NotNull EstadoNoticia estado) { }
