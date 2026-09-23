package ar.edu.ifts2.institucion.controller;

import ar.edu.ifts2.institucion.dto.*;
import ar.edu.ifts2.institucion.service.InstitucionService;
import ar.edu.ifts2.shared.dto.*;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/institucion")
@Tag(name = "Institucion")
@ApiResponse(responseCode = "200", description = "Contenido publicado")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID o parametros invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Contenido inexistente o no publicado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class InstitucionPublicaController {
    private final InstitucionService service;
    public InstitucionPublicaController(InstitucionService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar informacion institucional publicada")
    public InstitucionPublicaResponse obtener() { return service.obtenerPublicada(); }
}
