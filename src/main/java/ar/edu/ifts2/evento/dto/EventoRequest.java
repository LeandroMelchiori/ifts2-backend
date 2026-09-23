package ar.edu.ifts2.evento.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Schema(description = "Contenido completo en texto plano. Estado, fechas de auditoria y claves de archivos se administran por separado.")
public record EventoRequest(
        @NotBlank @Size(max = 200)
        String titulo,
        @NotBlank @Size(max = 500)
        String resumen,
        @NotBlank @Size(max = 20000)
        String descripcion,
        @NotNull
        Instant fechaInicio,
        @NotNull
        Instant fechaFin,
        @NotBlank @Size(max = 300)
        String lugar
) {
    @JsonIgnore
    @AssertTrue(message = "fechaFin debe ser posterior a fechaInicio")
    public boolean isPeriodoValido() {
        return fechaInicio == null || fechaFin == null || fechaFin.isAfter(fechaInicio);
    }
}
