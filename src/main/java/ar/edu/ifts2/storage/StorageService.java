package ar.edu.ifts2.storage;

import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.storage.model.StorageObject;
import java.util.List;
import java.io.InputStream;
import java.net.URI;

public interface StorageService {
    StoredFile upload(String namespace, String contentType, long size, InputStream content);
    void delete(String objectKey);
    URI resolvePublicUrl(String objectKey);
    default List<StorageObject> listObjects() {
        throw new StorageException(StorageException.Reason.INVENTORY_UNAVAILABLE);
    }
}
