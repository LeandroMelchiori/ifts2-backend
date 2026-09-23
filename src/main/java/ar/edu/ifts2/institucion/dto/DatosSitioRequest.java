package ar.edu.ifts2.institucion.dto;

import ar.edu.ifts2.shared.validation.HttpsUrl;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

public record DatosSitioRequest(
        @NotBlank @Size(max = 300) String direccion,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 50) String telefono,
        @Size(max = 500) String busquedaMapa,
        @HttpsUrl @Size(max = 2048) String sitioOficialUrl,
        @HttpsUrl @Size(max = 2048) String instagramUrl,
        @NotNull Boolean instagramVisible,
        @HttpsUrl @Size(max = 2048) String facebookUrl,
        @NotNull Boolean facebookVisible
) {
    @JsonIgnore
    @AssertTrue(message = "Un perfil visible requiere una URL")
    public boolean isPerfilesValidos() {
        return (!Boolean.TRUE.equals(instagramVisible) || instagramUrl != null && !instagramUrl.isBlank())
                && (!Boolean.TRUE.equals(facebookVisible) || facebookUrl != null && !facebookUrl.isBlank());
    }
}
