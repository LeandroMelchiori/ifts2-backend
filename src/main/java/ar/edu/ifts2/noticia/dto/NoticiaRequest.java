package ar.edu.ifts2.noticia.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ar.edu.ifts2.noticia.entity.AreaContenido;
import ar.edu.ifts2.shared.validation.HttpsUrl;
import java.time.LocalDate;

@Schema(description = "Texto plano. No admite estado ni fechas de auditoria. Area, mostrarEnNovedades y fecha omitidos conservan el valor al editar; al crear usan GENERAL, true y la fecha UTC actual. enlaceUrl null elimina el enlace.")
public record NoticiaRequest(
        @NotBlank @Size(max = 200) String titulo,
        @NotBlank @Size(max = 500) String resumen,
        @NotBlank @Size(max = 50000)
        @Schema(description = "Texto plano; el consumidor debe representarlo como texto, nunca como HTML")
        String contenido,
        AreaContenido area,
        Boolean mostrarEnNovedades,
        @HttpsUrl @Size(max = 2048) String enlaceUrl,
        @Schema(description = "Fecha editorial independiente de publicadaAt; no programa publicacion") LocalDate fecha
) {
    public NoticiaRequest(String titulo, String resumen, String contenido) {
        this(titulo, resumen, contenido, null, null, null, null);
    }
}
