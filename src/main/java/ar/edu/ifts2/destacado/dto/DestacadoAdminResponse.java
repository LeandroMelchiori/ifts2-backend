package ar.edu.ifts2.destacado.dto;

import java.net.URI;
import java.util.UUID;

public record DestacadoAdminResponse(int posicion, UUID noticiaId, UUID eventoId,
        String titulo, String resumen, URI portadaUrl, String enlaceUrl, boolean disponible) {
    public DestacadoPublicaResponse toPublic() {
        return new DestacadoPublicaResponse(posicion, noticiaId, eventoId, titulo, resumen, portadaUrl, enlaceUrl);
    }
}
