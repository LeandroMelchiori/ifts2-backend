package ar.edu.ifts2.panelrol.controller;

import ar.edu.ifts2.panelrol.dto.*;
import ar.edu.ifts2.panelrol.service.PanelRolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/panel-roles")
@Tag(name = "Roles del panel", description = "Solo ADMIN. Asignacion individual por usuario, no plantilla compartida.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "JWT invalido o currentPassword incorrecta")
@ApiResponse(responseCode = "403", description = "Requiere ADMIN")
@ApiResponse(responseCode = "400", description = "Solicitud invalida, seccion desconocida o gestionar sin ver")
@ApiResponse(responseCode = "409", description = "Usuario ya asignado, inactivo o ADMIN")
public class PanelRolController {
    private final PanelRolService service;
    public PanelRolController(PanelRolService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar asignaciones del panel", description = "Array ordenado por name e id. Siempre incluye las cuatro secciones.")
    public List<PanelRolResponse> listar() { return service.listar(); }

    @PostMapping
    @Operation(summary = "Asignar permisos a un EDITOR existente", description = "Opcion A: requiere usuario EDITOR activo previamente creado; 404 si no existe. currentPassword pertenece al ADMIN autenticado. No crea usuarios ni passwords temporales.")
    @ApiResponse(responseCode = "201", description = "Asignacion creada")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public ResponseEntity<PanelRolResponse> crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PanelRolRequest request) {
        var result = service.crear(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/api/admin/panel-roles/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplazar una asignacion", description = "Reemplazo completo. Secciones omitidas quedan false/false. Otro email reasigna a ese EDITOR existente; no cambia el email de login de nadie. Efectivo en la siguiente peticion con el mismo JWT.")
    @ApiResponse(responseCode = "404", description = "Asignacion o usuario inexistente")
    public PanelRolResponse actualizar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody PanelRolRequest request) {
        return service.actualizar(id, UUID.fromString(jwt.getSubject()), request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una asignacion", description = "Body JSON obligatorio con currentPassword del ADMIN. No elimina ni desactiva el usuario. La siguiente peticion al CMS sera rechazada salvo otra asignacion o rol ADMIN.")
    @ApiResponse(responseCode = "204", description = "Asignacion eliminada")
    @ApiResponse(responseCode = "404", description = "Asignacion inexistente")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody EliminarPanelRolRequest request) {
        service.eliminar(id, UUID.fromString(jwt.getSubject()), request.currentPassword());
        return ResponseEntity.noContent().build();
    }
}
