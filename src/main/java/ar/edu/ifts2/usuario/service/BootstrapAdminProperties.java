package ar.edu.ifts2.usuario.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 254) String email,
        String password
) {
    @Override
    public String toString() {
        return "BootstrapAdminProperties[REDACTED]";
    }
}
