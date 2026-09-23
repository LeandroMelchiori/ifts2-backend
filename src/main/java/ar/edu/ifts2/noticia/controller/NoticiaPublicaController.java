package ar.edu.ifts2.noticia.controller;

import ar.edu.ifts2.noticia.dto.NoticiaPublicaResponse;
import ar.edu.ifts2.noticia.dto.NoticiaResumenResponse;
import ar.edu.ifts2.noticia.service.NoticiaService;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/noticias")
@Tag(name = "News", description = "Consulta publica de noticias publicadas; textos sin formato HTML")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID o paginacion invalidos",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class NoticiaPublicaController {
    private final NoticiaService service;

    public NoticiaPublicaController(NoticiaService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar noticias publicadas", description = "Sin autenticacion. Orden: publicadaAt descendente, id ascendente. No incluye el cuerpo completo ni admite consultar otros estados.")
    @ApiResponse(responseCode = "200", description = "Pagina de resumenes publicados")
    public PageResponse<NoticiaResumenResponse> listar(
            @Parameter(description = "Pagina desde cero") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Cantidad por pagina, entre 1 y 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listarPublicadas(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una noticia publicada", description = "Borradores y archivadas responden 404, incluso con JWT administrativo.")
    @ApiResponse(responseCode = "200", description = "Noticia publicada")
    @ApiResponse(responseCode = "404", description = "Noticia inexistente o no publicada",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public NoticiaPublicaResponse obtener(@PathVariable UUID id) {
        return service.obtenerPublicada(id);
    }
}
