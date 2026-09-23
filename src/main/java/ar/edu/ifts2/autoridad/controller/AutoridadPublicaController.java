package ar.edu.ifts2.autoridad.controller;

import ar.edu.ifts2.autoridad.dto.*;
import ar.edu.ifts2.autoridad.service.AutoridadService;
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
@RequestMapping("/api/autoridades")
@Tag(name = "Autoridades")
@ApiResponse(responseCode = "200", description = "Contenido publicado")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID o parametros invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Contenido inexistente o no publicado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class AutoridadPublicaController {
    private final AutoridadService service;
    public AutoridadPublicaController(AutoridadService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar autoridades publicados", description = "Solo PUBLICADA. Orden editorial ascendente. Las URLs de archivos son null si storage esta deshabilitado.")
    public PageResponse<AutoridadPublicaResponse> listar(
            @Parameter(description = "Pagina desde cero, maximo 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @Parameter(description = "Cantidad de 1 a 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listarPublicados(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar autoridad publicado", description = "Borradores y archivados responden 404 incluso con JWT.")
    public AutoridadPublicaResponse obtener(@PathVariable UUID id) { return service.obtenerPublicada(id); }
}
