package ar.edu.ifts2.storage.supabase;

import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.storage.validation.FileValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import static ar.edu.ifts2.storage.StorageException.Reason.*;

public class SupabaseStorageService implements StorageService {
    private final RestClient client;
    private final SupabaseStorageProperties properties;
    private final FileValidator validator;
    private final ObjectMapper mapper;

    public SupabaseStorageService(RestClient client, SupabaseStorageProperties properties,
                                  FileValidator validator, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    public StoredFile upload(String namespace, String contentType, long size, InputStream content) {
        var file = validator.validate(namespace, contentType, size, content);
        try {
            return client.post().uri(objectPath() + "/" + file.objectKey())
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .contentLength(file.bytes().length).header("x-upsert", "false")
                    .body(file.bytes()).exchange((request, response) -> {
                        checkStatus(response, false);
                        return new StoredFile(file.objectKey(), file.contentType(), file.bytes().length);
                    });
        } catch (RestClientException ex) {
            throw new StorageException(PROVIDER_FAILURE);
        }
    }

    @Override
    public void delete(String objectKey) {
        validator.validateObjectKey(objectKey);
        try {
            client.method(HttpMethod.DELETE).uri(objectPath()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(objectKey))).exchange((request, response) -> {
                        checkStatus(response, true);
                        JsonNode deleted = readJson(response);
                        if (deleted == null || !deleted.isArray()) throw new StorageException(PROVIDER_FAILURE);
                        for (JsonNode object : deleted) {
                            if (objectKey.equals(object.path("name").asText())) return null;
                        }
                        // La API de borrado multiple devuelve una lista vacia si la clave no existe.
                        throw new StorageException(NOT_FOUND);
                    });
        } catch (RestClientException ex) {
            throw new StorageException(PROVIDER_FAILURE);
        }
    }

    @Override
    public URI resolvePublicUrl(String objectKey) {
        validator.validateObjectKey(objectKey);
        return properties.url().resolve("/storage/v1/object/public/" + properties.bucket() + "/" + objectKey);
    }

    private String objectPath() {
        return "/storage/v1/object/" + properties.bucket();
    }

    private void checkStatus(ClientHttpResponse response, boolean deleting) throws IOException {
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) return;
        JsonNode error = readJson(response);
        String code = error == null ? "" : error.path("code").asText();
        if (deleting && (code.equals("NoSuchKey") || code.equals("not_found")
                || (status == 404 && code.isEmpty()))) throw new StorageException(NOT_FOUND);
        if (status == 413 || code.equals("EntityTooLarge")) throw new StorageException(TOO_LARGE);
        if (status == 415 || code.equals("InvalidMimeType")) throw new StorageException(UNSUPPORTED_TYPE);
        // Credenciales, bucket inexistente, redirecciones y errores internos son fallos del proveedor.
        throw new StorageException(PROVIDER_FAILURE);
    }

    private JsonNode readJson(ClientHttpResponse response) throws IOException {
        byte[] body = response.getBody().readNBytes(65_537);
        if (body.length > 65_536) throw new StorageException(PROVIDER_FAILURE);
        try {
            return mapper.readTree(body);
        } catch (IOException ex) {
            return null;
        }
    }
}
