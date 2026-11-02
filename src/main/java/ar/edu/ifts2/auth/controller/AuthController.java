package ar.edu.ifts2.auth.controller;

import ar.edu.ifts2.auth.dto.ChangeOwnEmailRequest;
import ar.edu.ifts2.auth.dto.ChangeOwnPasswordRequest;
import ar.edu.ifts2.auth.dto.LoginRequest;
import ar.edu.ifts2.auth.dto.LoginResponse;
import ar.edu.ifts2.auth.dto.MeResponse;
import ar.edu.ifts2.auth.service.AuthService;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
    @Operation(summary = "Perfil de la sesion actual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil sin credenciales"),
            @ApiResponse(responseCode = "401", description = "JWT ausente o invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/me/password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar la propia password",
            description = "Requiere la password actual. Minimo 12 caracteres en la nueva.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password actualizada", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT ausente o invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Password actual incorrecta",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ChangeOwnPasswordRequest request) {
        authService.changeOwnPassword(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/email")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar el propio email",
            description = "Requiere la password actual. El email debe ser unico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email actualizado"),
            @ApiResponse(responseCode = "401", description = "JWT ausente o invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Password actual incorrecta o email duplicado",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MeResponse changeOwnEmail(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ChangeOwnEmailRequest request) {
        return authService.changeOwnEmail(UUID.fromString(jwt.getSubject()), request);
    }
}
