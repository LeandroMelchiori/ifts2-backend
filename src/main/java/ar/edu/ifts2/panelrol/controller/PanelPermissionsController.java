package ar.edu.ifts2.panelrol.controller;

import ar.edu.ifts2.panelrol.dto.PanelPermissionsResponse;
import ar.edu.ifts2.panelrol.service.PanelRolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@Tag(name = "Authentication")
@SecurityRequirement(name = "bearerAuth")
public class PanelPermissionsController {
    private final PanelRolService service;
    public PanelPermissionsController(PanelRolService service) { this.service = service; }
    @GetMapping("/api/auth/me/panel-permissions")
    @Operation(summary = "Consultar permisos efectivos", description = "Lee la DB, no claims de permisos ni localStorage. ADMIN: fullAccess=true y todo permitido. Sin asignacion: roleName=null y todo false.")
    public ResponseEntity<PanelPermissionsResponse> permisos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.permisos(UUID.fromString(jwt.getSubject())));
    }
}
