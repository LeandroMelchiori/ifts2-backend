package ar.edu.ifts2.enlace.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import ar.edu.ifts2.shared.validation.HttpsUrl;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record EnlaceRequest(
        @NotBlank @Size(max = 150)
        String titulo,
        @Size(max = 500)
        String descripcion,
        @NotBlank @Size(max = 2048) @HttpsUrl
        String url,
        @NotBlank @Size(max = 100)
        String categoria,
        @NotNull @Min(0) @Max(10000)
        Integer orden
) { }
