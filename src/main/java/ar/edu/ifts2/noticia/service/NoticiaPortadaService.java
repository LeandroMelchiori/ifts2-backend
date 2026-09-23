package ar.edu.ifts2.noticia.service;

import ar.edu.ifts2.noticia.dto.NoticiaPortadaResponse;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.validation.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Service
public class NoticiaPortadaService {
    private static final Logger log = LoggerFactory.getLogger(NoticiaPortadaService.class);
    private final NoticiaRepository repository;
    private final ObjectProvider<StorageService> storageProvider;
    private final FileValidator validator;

    public NoticiaPortadaService(NoticiaRepository repository, ObjectProvider<StorageService> storageProvider,
                                 FileValidator validator) {
        this.repository = repository;
        this.storageProvider = storageProvider;
        this.validator = validator;
    }

    @Transactional
    public NoticiaPortadaResponse subir(UUID id, String contentType, long size, InputStream content) {
        Noticia noticia = findForUpdate(id);
        StorageService storage = requireStorage();
        var file = validator.validateImage("noticias", contentType, size, content);
        String previousKey = noticia.getPortadaObjectKey();
        var stored = storage.upload("noticias", file.contentType(), file.bytes().length,
                new ByteArrayInputStream(file.bytes()));
        // El storage no participa en la transaccion SQL: compensar solo con resultado conocido.
        registerCleanup(storage, previousKey, stored.objectKey());
        noticia.cambiarPortada(stored.objectKey());
        repository.flush();
        return new NoticiaPortadaResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitar(UUID id) {
        Noticia noticia = findForUpdate(id);
        String previousKey = noticia.getPortadaObjectKey();
        if (previousKey == null) return;
        StorageService storage = requireStorage();
        registerCleanup(storage, previousKey, null);
        noticia.cambiarPortada(null);
        repository.flush();
    }

    public URI resolvePublicUrl(String objectKey) {
        if (objectKey == null) return null;
        StorageService storage = storageProvider.getIfAvailable();
        return storage == null ? null : storage.resolvePublicUrl(objectKey);
    }

    private StorageService requireStorage() {
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) throw new StorageException(StorageException.Reason.STORAGE_DISABLED);
        return storage;
    }

    private Noticia findForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Noticia no encontrada"));
    }

    private void registerCleanup(StorageService storage, String previousKey, String uploadedKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteUnused(storage, previousKey);
                } else if (status == STATUS_ROLLED_BACK) {
                    deleteUnused(storage, uploadedKey);
                } else {
                    log.warn("Resultado SQL incierto para portada; verificar claves anterior={} nueva={}", previousKey, uploadedKey);
                }
            }
        });
    }

    private void deleteUnused(StorageService storage, String objectKey) {
        if (objectKey == null) return;
        try {
            storage.delete(objectKey);
        } catch (StorageException ex) {
            if (ex.getReason() != StorageException.Reason.NOT_FOUND) {
                log.warn("Limpieza pendiente de portada objectKey={} motivo={}", objectKey, ex.getReason());
            }
        } catch (RuntimeException ex) {
            // No exponer mensajes del proveedor ni convertir un commit confirmado en un error HTTP.
            log.warn("Limpieza pendiente de portada objectKey={} tipo={}", objectKey, ex.getClass().getSimpleName());
        }
    }
}
