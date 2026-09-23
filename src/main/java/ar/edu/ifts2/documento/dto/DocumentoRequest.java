package ar.edu.ifts2.documento.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import ar.edu.ifts2.documento.entity.TipoDocumento;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record DocumentoRequest(
        @NotBlank @Size(max = 200)
        String titulo,
        @Size(max = 2000)
        String descripcion,
        @NotNull
        TipoDocumento tipo
) { }
