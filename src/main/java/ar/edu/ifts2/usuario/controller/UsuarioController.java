package ar.edu.ifts2.usuario.controller;

import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.ApiError;
import ar.edu.ifts2.usuario.dto.ActualizarUsuarioRequest;
import ar.edu.ifts2.usuario.dto.CambiarPasswordRequest;
import ar.edu.ifts2.usuario.dto.CrearUsuarioRequest;
import ar.edu.ifts2.usuario.dto.UsuarioResponse;
import ar.edu.ifts2.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/usuarios")
@Tag(name = "Users", description = "Administracion exclusiva de ADMIN")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID, parametros o request invalidos",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente, invalido o cuenta sin acceso",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere rol ADMIN",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Incluye activos e inactivos. Orden: createdAt descendente, id ascendente.")
    @ApiResponse(responseCode = "200", description = "Pagina de usuarios sin credenciales")
    public PageResponse<UsuarioResponse> listar(
            @Parameter(description = "Pagina desde cero") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Cantidad por pagina, entre 1 y 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listar(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UsuarioResponse obtener(@Parameter(description = "UUID del usuario") @PathVariable UUID id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crear un usuario activo", description = "Email unico normalizado. Password: minimo 12 caracteres y maximo 72 bytes UTF-8; se almacena con BCrypt.")
    @ApiResponse(responseCode = "201", description = "Usuario creado; Location identifica el recurso")
    @ApiResponse(responseCode = "409", description = "Email duplicado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        UsuarioResponse response = service.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/usuarios/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos administrables", description = "Requiere los cinco campos. activo=false desactiva y activo=true reactiva. No admite password ni passwordHash. Prohibe perder al ultimo ADMIN activo y desactivar o degradar la propia cuenta.")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado")
    @ApiResponse(responseCode = "404", description = "Usuario inexistente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email duplicado o proteccion administrativa", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UsuarioResponse actualizar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ActualizarUsuarioRequest request) {
        return service.actualizar(id, UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Restablecer password", description = "Operacion exclusiva de ADMIN, tambien para su propia cuenta. Minimo 12 caracteres, maximo 72 bytes UTF-8. Los JWT ya emitidos vencen segun su TTL.")
    @ApiResponse(responseCode = "204", description = "Password actualizada", content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuario inexistente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> cambiarPassword(@PathVariable UUID id,
                                               @Valid @RequestBody CambiarPasswordRequest request) {
        service.cambiarPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
