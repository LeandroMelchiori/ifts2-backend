package ar.edu.ifts2.documento.controller;

import ar.edu.ifts2.documento.dto.*;
import ar.edu.ifts2.documento.service.DocumentoService;
import ar.edu.ifts2.shared.dto.*;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Documentos")
@ApiResponse(responseCode = "200", description = "Contenido publicado")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID o parametros invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Contenido inexistente o no publicado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class DocumentoPublicaController {
    private final DocumentoService service;
    public DocumentoPublicaController(DocumentoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar documentos publicados", description = "Solo PUBLICADA. Orden por ultima publicacion descendente. Filtro opcional por tipo. Las URLs de archivos son null si storage esta deshabilitado.")
    public PageResponse<DocumentoPublicaResponse> listar(
            @Parameter(description = "Pagina desde cero, maximo 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @Parameter(description = "Cantidad de 1 a 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) ar.edu.ifts2.documento.entity.TipoDocumento tipo) {
        return service.listarPublicados(page, size, tipo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar documento publicado", description = "Borradores y archivados responden 404 incluso con JWT.")
    public DocumentoPublicaResponse obtener(@PathVariable UUID id) { return service.obtenerPublicada(id); }
}
