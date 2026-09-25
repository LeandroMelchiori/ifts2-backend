package ar.edu.ifts2.destacado.controller;

import ar.edu.ifts2.destacado.dto.*;
import ar.edu.ifts2.destacado.service.DestacadoService;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/destacados")
@Tag(name = "Destacados Administration")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Lista invalida o mas de seis elementos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Contenido inexistente", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Duplicado o contenido sin publicar", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class DestacadoAdminController {
    private final DestacadoService service;
    public DestacadoAdminController(DestacadoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar seleccion y orden del carrusel", description = "Incluye seleccionados retirados con disponible=false.")
    public List<DestacadoAdminResponse> listar() { return service.listarAdmin(); }

    @PutMapping
    @Operation(summary = "Reemplazar la seleccion ordenada de destacados",
            description = "De cero a seis noticias/eventos publicados o posts Meta visibles, sin duplicados. Cada referencia tiene exactamente noticiaId, eventoId o metaPostId. El orden del array define posiciones 1..6. Lista vacia limpia el carrusel. Guardado atomico; ante ediciones simultaneas prevalece la ultima lista guardada.")
    public List<DestacadoAdminResponse> guardar(@Valid @RequestBody DestacadosRequest request) {
        return service.reemplazar(request);
    }
}
