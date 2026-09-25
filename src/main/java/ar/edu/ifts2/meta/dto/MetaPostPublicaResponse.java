package ar.edu.ifts2.meta.dto;

import ar.edu.ifts2.meta.entity.TipoMedia;
import java.net.URI;
import java.time.Instant;

public record MetaPostPublicaResponse(String id, String texto, URI imagenUrl, URI thumbnailUrl,
        TipoMedia mediaType, Instant fechaPublicacion, String linkOriginal) { }
