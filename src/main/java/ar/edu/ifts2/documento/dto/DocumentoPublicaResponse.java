package ar.edu.ifts2.documento.dto;

import ar.edu.ifts2.documento.entity.Documento;
import ar.edu.ifts2.documento.entity.TipoDocumento;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record DocumentoPublicaResponse(UUID id, String titulo, String descripcion, TipoDocumento tipo, URI archivoUrl, Instant publicadaAt) {
    public static DocumentoPublicaResponse from(Documento item, URI url) {
        return new DocumentoPublicaResponse(item.getId(), item.getTitulo(), item.getDescripcion(), item.getTipo(), url, item.getPublicadaAt());
    }
}
