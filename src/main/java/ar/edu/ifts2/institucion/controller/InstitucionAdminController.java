package ar.edu.ifts2.institucion.controller;

import ar.edu.ifts2.institucion.dto.*;
import ar.edu.ifts2.institucion.service.InstitucionService;
import ar.edu.ifts2.shared.dto.*;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/institucion")
@Tag(name = "Institucion Administration")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Request, parametros o UUID invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Recurso no encontrado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Conflicto con los datos o el estado actual", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class InstitucionAdminController {
    private final InstitucionService service;
    public InstitucionAdminController(InstitucionService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar informacion institucional en cualquier estado")
    @ApiResponse(responseCode = "200", description = "Contenido institucional")
    public InstitucionAdminResponse obtener() { return service.obtener(); }

    @PutMapping
    @Operation(summary = "Guardar informacion institucional", description = "Reemplazo completo. El primer PUT crea un BORRADOR; posteriores conservan el estado. Solo existe un registro institucional.")
    @ApiResponse(responseCode = "200", description = "Contenido guardado")
    public InstitucionAdminResponse actualizar(@Valid @RequestBody InstitucionRequest request) { return service.actualizar(request); }

    @PutMapping("/estado")
    @Operation(summary = "Publicar, retirar o archivar informacion institucional")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    public InstitucionAdminResponse cambiarEstado(@Valid @RequestBody CambiarEstadoRequest request) {
        return service.cambiarEstado(request.estado());
    }

    @PutMapping("/datos-sitio")
    @Operation(summary = "Guardar contacto, mapa y perfiles sociales",
            description = "Reemplaza esos campos; conserva nombre, descripcion, horarios y estado. Requiere Institucion creada. Un perfil oculto no expone su URL en la API publica. No conecta con Meta.")
    public InstitucionAdminResponse actualizarDatosSitio(@Valid @RequestBody DatosSitioRequest request) {
        return service.actualizarDatosSitio(request);
    }
}
