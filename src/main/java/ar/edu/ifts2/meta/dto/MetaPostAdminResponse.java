package ar.edu.ifts2.meta.dto;

import ar.edu.ifts2.meta.entity.TipoMedia;
import java.net.URI;
import java.time.Instant;

public record MetaPostAdminResponse(String id, String texto, URI imagenUrl, URI thumbnailUrl,
        TipoMedia mediaType, Instant fechaPublicacion, String linkOriginal, boolean visible,
        boolean tieneStorage, String objectKey) {
    public MetaPostPublicaResponse toPublic() {
        return new MetaPostPublicaResponse(id, texto, imagenUrl, thumbnailUrl, mediaType, fechaPublicacion, linkOriginal);
    }
}
