package ar.edu.ifts2.noticia.service;

import ar.edu.ifts2.noticia.dto.NoticiaPortadaResponse;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.storage.ArchivoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Service
public class NoticiaPortadaService {
    private final NoticiaRepository repository;
    private final ArchivoStorageService storage;

    public NoticiaPortadaService(NoticiaRepository repository, ArchivoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public NoticiaPortadaResponse subir(UUID id, String contentType, long size, InputStream content) {
        Noticia noticia = findForUpdate(id);
        var stored = storage.replaceImage("noticias", noticia.getPortadaObjectKey(), contentType, size, content);
        noticia.cambiarPortada(stored.objectKey());
        repository.flush();
        return new NoticiaPortadaResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitar(UUID id) {
        Noticia noticia = findForUpdate(id);
        storage.removeAfterCommit(noticia.getPortadaObjectKey());
        noticia.cambiarPortada(null);
        repository.flush();
    }

    public URI resolvePublicUrl(String objectKey) { return storage.resolvePublicUrl(objectKey); }

    private Noticia findForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Noticia no encontrada"));
    }
}
