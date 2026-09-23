package ar.edu.ifts2.documento.dto;

import ar.edu.ifts2.documento.entity.Documento;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.documento.entity.TipoDocumento;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record DocumentoAdminResponse(UUID id, String titulo, String descripcion, TipoDocumento tipo, URI archivoUrl, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt, String archivoObjectKey) {
    public static DocumentoAdminResponse from(Documento item, URI url) {
        return new DocumentoAdminResponse(item.getId(), item.getTitulo(), item.getDescripcion(), item.getTipo(), url, item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt(), item.getArchivoObjectKey());
    }
}
