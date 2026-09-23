package ar.edu.ifts2.storage;

import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.storage.validation.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

@Service
public class ArchivoStorageService {
    private static final Logger log = LoggerFactory.getLogger(ArchivoStorageService.class);
    private final ObjectProvider<StorageService> provider;
    private final FileValidator validator;

    public ArchivoStorageService(ObjectProvider<StorageService> provider, FileValidator validator) {
        this.provider = provider;
        this.validator = validator;
    }

    public StoredFile replaceImage(String namespace, String previousKey, String mime, long size, InputStream content) {
        StorageService storage = requireStorage();
        return upload(storage, namespace, previousKey, validator.validateImage(namespace, mime, size, content));
    }

    public StoredFile replacePdf(String namespace, String previousKey, String mime, long size, InputStream content) {
        StorageService storage = requireStorage();
        if (mime == null || !"application/pdf".equalsIgnoreCase(mime.strip())) {
            throw new StorageException(StorageException.Reason.UNSUPPORTED_TYPE);
        }
        return upload(storage, namespace, previousKey, validator.validate(namespace, mime, size, content));
    }

    private StoredFile upload(StorageService storage, String namespace, String previousKey, FileValidator.ValidatedFile file) {
        requireTransaction();
        var stored = storage.upload(namespace, file.contentType(), file.bytes().length, new ByteArrayInputStream(file.bytes()));
        registerCleanup(storage, previousKey, stored.objectKey());
        return stored;
    }

    public void removeAfterCommit(String objectKey) {
        if (objectKey == null) return;
        requireTransaction();
        registerCleanup(requireStorage(), objectKey, null);
    }

    public URI resolvePublicUrl(String objectKey) {
        if (objectKey == null) return null;
        StorageService storage = provider.getIfAvailable();
        return storage == null ? null : storage.resolvePublicUrl(objectKey);
    }

    private StorageService requireStorage() {
        StorageService storage = provider.getIfAvailable();
        if (storage == null) throw new StorageException(StorageException.Reason.STORAGE_DISABLED);
        return storage;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La asociacion de archivos requiere una transaccion");
        }
    }

    private void registerCleanup(StorageService storage, String previousKey, String uploadedKey) {
        // El storage no participa en SQL: borrar solo con resultado de transaccion conocido.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) deleteUnused(storage, previousKey);
                else if (status == STATUS_ROLLED_BACK) deleteUnused(storage, uploadedKey);
                else log.warn("Resultado SQL incierto; verificar claves anterior={} nueva={}", previousKey, uploadedKey);
            }
        });
    }

    private void deleteUnused(StorageService storage, String key) {
        if (key == null) return;
        try {
            storage.delete(key);
        } catch (StorageException ex) {
            if (ex.getReason() != StorageException.Reason.NOT_FOUND) {
                log.warn("Limpieza pendiente de archivo objectKey={} motivo={}", key, ex.getReason());
            }
        } catch (RuntimeException ex) {
            log.warn("Limpieza pendiente de archivo objectKey={} tipo={}", key, ex.getClass().getSimpleName());
        }
    }
}
