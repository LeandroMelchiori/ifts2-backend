package ar.edu.ifts2.shared.controller;

import ar.edu.ifts2.shared.dto.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Verificacion")
public class ProbeController {
    @GetMapping("/api/health")
    @Operation(summary = "Verificar que la API responde")
    public StatusResponse health() {
        return new StatusResponse("UP");
    }

    @GetMapping("/api/admin/test")
    @Operation(summary = "Prueba temporal de acceso ADMIN o EDITOR")
    @SecurityRequirement(name = "bearerAuth")
    public StatusResponse adminTest() {
        return new StatusResponse("OK");
    }

    @GetMapping("/api/admin/usuarios/test")
    @Operation(summary = "Prueba temporal de acceso exclusivo ADMIN")
    @SecurityRequirement(name = "bearerAuth")
    public StatusResponse usuariosTest() {
        return new StatusResponse("OK");
    }
}
