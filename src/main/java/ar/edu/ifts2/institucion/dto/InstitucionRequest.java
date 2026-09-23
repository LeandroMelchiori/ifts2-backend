package ar.edu.ifts2.institucion.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record InstitucionRequest(
        @NotBlank @Size(max = 200)
        String nombre,
        @NotBlank @Size(max = 20000)
        String descripcion,
        @NotBlank @Size(max = 300)
        String direccion,
        @NotBlank @Size(max = 254) @Email
        String email,
        @Size(max = 50)
        String telefono,
        @Size(max = 500)
        String horariosAtencion
) { }
