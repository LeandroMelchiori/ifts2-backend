package ar.edu.ifts2.auth.controller;

import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.dto.LoginResponse;
import ar.edu.ifts2.auth.service.AuthService;
import ar.edu.ifts2.auth.dto.ChangeOwnEmailRequest;
import ar.edu.ifts2.auth.dto.ChangeOwnPasswordRequest;
import ar.edu.ifts2.auth.dto.MeResponse;
import ar.edu.ifts2.auth.dto.VerifyPasswordRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion y obtener un JWT")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(request));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar la propia cuenta", description = "Disponible sin rol de panel. No devuelve credenciales.")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.me(UUID.fromString(jwt.getSubject())));
    }

    @PutMapping("/me/email")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar el propio email", description = "Requiere currentPassword. 409 si es incorrecta o el email esta ocupado. Conserva la asignacion del panel por ID y los JWT emitidos.")
    public ResponseEntity<MeResponse> email(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody ChangeOwnEmailRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.changeOwnEmail(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping("/me/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar la propia password", description = "409 si currentPassword es incorrecta. Nueva password: minimo 12 caracteres, maximo 72 bytes UTF-8. JWT existentes conservan su TTL; no se emite otro token.")
    @ApiResponse(responseCode = "204", description = "Password actualizada")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ChangeOwnPasswordRequest request) {
        authService.changeOwnPassword(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Verificar la password actual", description = "204 si coincide; 401 si no. No renueva ni emite JWT. No reemplaza la confirmacion obligatoria de cada mutacion de panel-roles.")
    @ApiResponse(responseCode = "204", description = "Password verificada")
    @ApiResponse(responseCode = "401", description = "Password incorrecta o JWT/cuenta sin acceso")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody VerifyPasswordRequest request) {
        authService.verifyPassword(UUID.fromString(jwt.getSubject()), request.password());
        return ResponseEntity.noContent().build();
    }
}
