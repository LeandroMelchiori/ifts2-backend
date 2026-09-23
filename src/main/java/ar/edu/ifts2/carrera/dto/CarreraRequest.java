package ar.edu.ifts2.carrera.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record CarreraRequest(
        @NotBlank @Size(max = 200)
        String nombre,
        @NotBlank @Size(max = 200)
        String tituloOtorgado,
        @NotBlank @Size(max = 20000)
        String descripcion,
        @NotBlank @Size(max = 100)
        String duracion,
        @NotBlank @Size(max = 100)
        String modalidad,
        @Size(max = 10000)
        String requisitosIngreso,
        @NotNull @Min(0) @Max(10000)
        Integer orden
) { }
