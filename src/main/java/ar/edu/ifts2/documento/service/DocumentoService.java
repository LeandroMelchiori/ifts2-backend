package ar.edu.ifts2.documento.service;

import ar.edu.ifts2.documento.dto.*;
import ar.edu.ifts2.documento.entity.Documento;
import ar.edu.ifts2.documento.repository.DocumentoRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.documento.entity.TipoDocumento;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.storage.ArchivoStorageService;
import ar.edu.ifts2.storage.dto.StorageUrlResponse;
import java.io.InputStream;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentoService {
    private final DocumentoRepository repository;
    private final ArchivoStorageService storage;

    public DocumentoService(DocumentoRepository repository, ArchivoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public PageResponse<DocumentoPublicaResponse> listarPublicados(int page, int size, TipoDocumento tipo) {
        var pageable = PageRequest.of(page, size, Sort.by("publicadaAt").descending().and(Sort.by("id")));
        var items = tipo == null ? repository.findByEstado(EstadoPublicacion.PUBLICADA, pageable)
                : repository.findByEstadoAndTipo(EstadoPublicacion.PUBLICADA, tipo, pageable);
        return PageResponse.from(items.map(this::toPublic));
    }

    public DocumentoPublicaResponse obtenerPublicada(UUID id) {
        return toPublic(repository.findByIdAndEstado(id, EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public PageResponse<DocumentoAdminResponse> listar(EstadoPublicacion estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var items = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(items.map(this::toAdmin));
    }

    public DocumentoAdminResponse obtener(UUID id) { return toAdmin(repository.findById(id).orElseThrow(this::notFound)); }

    @Transactional
    public DocumentoAdminResponse crear(DocumentoRequest request) {
        return toAdmin(repository.saveAndFlush(new Documento(request.titulo(), request.descripcion(), request.tipo())));
    }

    @Transactional
    public DocumentoAdminResponse actualizar(UUID id, DocumentoRequest request) {
        Documento item = findForUpdate(id);
        item.actualizar(request.titulo(), request.descripcion(), request.tipo());
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public DocumentoAdminResponse cambiarEstado(UUID id, EstadoPublicacion estado) {
        Documento item = findForUpdate(id);
        if (estado == EstadoPublicacion.PUBLICADA && item.getArchivoObjectKey() == null) {
            throw new BusinessConflictException("El documento necesita un PDF antes de publicarse");
        }
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    private Documento findForUpdate(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }

    @Transactional
    public StorageUrlResponse subirArchivo(UUID id, String mime, long size, InputStream content) {
        Documento item = findForUpdate(id);
        var stored = storage.replacePdf("documentos", item.getArchivoObjectKey(), mime, size, content);
        item.cambiarArchivo(stored.objectKey());
        repository.flush();
        return new StorageUrlResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitarArchivo(UUID id) {
        Documento item = findForUpdate(id);
        if (item.getEstado() == EstadoPublicacion.PUBLICADA) {
            throw new BusinessConflictException("Retire el documento a BORRADOR antes de quitar su PDF");
        }
        storage.removeAfterCommit(item.getArchivoObjectKey());
        item.cambiarArchivo(null);
        repository.flush();
    }

    private DocumentoAdminResponse toAdmin(Documento item) { return DocumentoAdminResponse.from(item, storage.resolvePublicUrl(item.getArchivoObjectKey())); }
    private DocumentoPublicaResponse toPublic(Documento item) { return DocumentoPublicaResponse.from(item, storage.resolvePublicUrl(item.getArchivoObjectKey())); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Documentos: recurso no encontrado"); }
}
