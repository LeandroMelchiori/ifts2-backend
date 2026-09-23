package ar.edu.ifts2.noticia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Contenido completo para crear o actualizar. No admite estado ni fechas.")
public record NoticiaRequest(
        @NotBlank @Size(max = 200) String titulo,
        @NotBlank @Size(max = 500) String resumen,
        @NotBlank @Size(max = 50000)
        @Schema(description = "Texto plano; el consumidor debe representarlo como texto, nunca como HTML")
        String contenido
) { }
