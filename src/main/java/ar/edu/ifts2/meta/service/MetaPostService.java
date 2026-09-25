package ar.edu.ifts2.meta.service;

import ar.edu.ifts2.destacado.repository.DestacadoRepository;
import ar.edu.ifts2.meta.client.*;
import ar.edu.ifts2.meta.dto.*;
import ar.edu.ifts2.meta.entity.MetaPost;
import ar.edu.ifts2.meta.repository.MetaPostRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.*;
import ar.edu.ifts2.storage.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.util.*;

@Service
public class MetaPostService {
    private final MetaPostRepository repository;
    private final DestacadoRepository destacados;
    private final MetaGraphClient graph;
    private final MetaPreviewDownloader downloader;
    private final ArchivoStorageService archivos;
    private final ObjectProvider<StorageService> storage;
    private final TransactionTemplate tx;

    public MetaPostService(MetaPostRepository repository, DestacadoRepository destacados, MetaGraphClient graph,
            MetaPreviewDownloader downloader, ArchivoStorageService archivos, ObjectProvider<StorageService> storage,
            PlatformTransactionManager transactions) {
        this.repository = repository;
        this.destacados = destacados;
        this.graph = graph;
        this.downloader = downloader;
        this.archivos = archivos;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactions);
    }

    public MetaSyncResponse sincronizar() {
        List<MetaMedia> media = graph.recientes();
        return tx.execute(status -> {
            // Mismo orden de locks que destacados, visibilidad y purge; tambien serializa el primer upsert.
            destacados.bloquearCarrusel();
            List<MetaPostAdminResponse> posts = new ArrayList<>();
            for (MetaMedia item : media) {
                MetaPost post = repository.findById(item.id()).orElseGet(() -> new MetaPost(item.id()));
                post.actualizar(item.texto(), item.tipo(), item.fecha(), item.enlace());
                repository.save(post);
                posts.add(toAdmin(post));
            }
            repository.flush();
            return new MetaSyncResponse(posts.size(), "Sincronizacion completada", posts);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<MetaPostAdminResponse> listarAdmin(Boolean visible, int page, int size) {
        return PageResponse.from(repository.buscar(visible, page(page, size)).map(this::toAdmin));
    }

    @Transactional(readOnly = true)
    public PageResponse<MetaPostPublicaResponse> listarPublicos(int page, int size) {
        return PageResponse.from(repository.buscar(true, page(page, size)).map(this::toAdmin).map(MetaPostAdminResponse::toPublic));
    }

    @Transactional
    public MetaPostAdminResponse visibilidad(String id, boolean visible) {
        destacados.bloquearCarrusel();
        MetaPost post = require(id);
        if (visible && post.getObjectKey() == null) {
            requireStorage();
            // Obtener una URL fresca; las URLs firmadas del CDN no son permanentes.
            MetaMedia media = graph.obtener(id);
            var preview = downloader.descargar(media.preview());
            var file = archivos.replaceImage("meta", null, preview.contentType(), preview.bytes().length,
                    new ByteArrayInputStream(preview.bytes()));
            post.asociarArchivo(file.objectKey());
        }
        post.cambiarVisibilidad(visible);
        repository.flush();
        return toAdmin(post);
    }

    @Transactional
    public MetaStorageResponse liberarStorage(String id) {
        destacados.bloquearCarrusel();
        MetaPost post = require(id);
        if (post.isVisible() || destacados.existsByMetaPostId(id)) {
            throw new BusinessConflictException("Ocultar la publicacion y retirarla de destacados antes de liberar su archivo");
        }
        String key = post.getObjectKey();
        if (key == null) return new MetaStorageResponse(true, null, "La publicacion no tiene copia en storage");
        // Confirmar borrado antes de responder. Si SQL falla despues, el reintento acepta NOT_FOUND.
        try { requireStorage().delete(key); }
        catch (StorageException ex) {
            if (ex.getReason() != StorageException.Reason.NOT_FOUND) throw ex;
        }
        post.asociarArchivo(null);
        repository.flush();
        return new MetaStorageResponse(true, key, "Storage liberado");
    }

    private MetaPost require(String id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Publicacion Meta no encontrada"));
    }

    private StorageService requireStorage() {
        StorageService service = storage.getIfAvailable();
        if (service == null) throw new StorageException(StorageException.Reason.STORAGE_DISABLED);
        return service;
    }

    private MetaPostAdminResponse toAdmin(MetaPost post) {
        var url = archivos.resolvePublicUrl(post.getObjectKey());
        return new MetaPostAdminResponse(post.getId(), post.getTexto(), url, url, post.getMediaType(),
                post.getFechaPublicacion(), post.getLinkOriginal(), post.isVisible(), post.getObjectKey() != null, post.getObjectKey());
    }

    private Pageable page(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("fechaPublicacion"), Sort.Order.asc("id")));
    }
}
