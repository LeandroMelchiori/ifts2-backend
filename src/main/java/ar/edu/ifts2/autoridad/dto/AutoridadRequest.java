package ar.edu.ifts2.autoridad.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record AutoridadRequest(
        @NotBlank @Size(max = 100)
        String nombre,
        @NotBlank @Size(max = 100)
        String apellido,
        @NotBlank @Size(max = 150)
        String cargo,
        @Size(max = 2000)
        String descripcion,
        @NotNull @Min(0) @Max(10000)
        Integer orden
) { }
