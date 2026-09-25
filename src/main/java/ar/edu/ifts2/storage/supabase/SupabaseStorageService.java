package ar.edu.ifts2.storage.supabase;

import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.model.StoredFile;
import ar.edu.ifts2.storage.model.StorageObject;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
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

    @Override
    public List<StorageObject> listObjects() {
        var prefixes = new ArrayDeque<String>();
        prefixes.add("");
        var result = new ArrayList<StorageObject>();
        var seen = new HashSet<String>();
        int requests = 0;
        try {
            while (!prefixes.isEmpty()) {
                String prefix = prefixes.remove();
                for (int offset = 0; ; offset += 100) {
                    // No informar una cifra parcial si el bucket excede el barrido acotado.
                    if (++requests > 100) throw new StorageException(INVENTORY_UNAVAILABLE);
                    JsonNode page = client.post().uri("/storage/v1/object/list/" + properties.bucket())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("prefix", prefix, "limit", 100, "offset", offset,
                                    "sortBy", Map.of("column", "name", "order", "asc")))
                            .exchange((request, response) -> {
                                checkStatus(response, false);
                                return readJson(response, 1_048_576);
                            });
                    if (page == null || !page.isArray() || page.size() > 100) throw new StorageException(PROVIDER_FAILURE);
                    for (JsonNode item : page) {
                        String name = item.path("name").asText();
                        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                            throw new StorageException(PROVIDER_FAILURE);
                        }
                        String key = prefix + name;
                        if (key.length() > 1024 || !seen.add(key)) throw new StorageException(INVENTORY_UNAVAILABLE);
                        if (!item.hasNonNull("id") && !item.hasNonNull("metadata")) prefixes.add(key + "/");
                        else {
                            JsonNode size = item.path("metadata").path("size");
                            if (!size.isIntegralNumber() || !size.canConvertToLong() || size.longValue() < 0) {
                                throw new StorageException(PROVIDER_FAILURE);
                            }
                            result.add(new StorageObject(key, size.longValue()));
                        }
                    }
                    if (page.size() < 100) break;
                }
            }
            return List.copyOf(result);
        } catch (RestClientException ex) {
            throw new StorageException(PROVIDER_FAILURE);
        }
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
        return readJson(response, 65_536);
    }

    private JsonNode readJson(ClientHttpResponse response, int maxBytes) throws IOException {
        byte[] body = response.getBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new StorageException(PROVIDER_FAILURE);
        try {
            return mapper.readTree(body);
        } catch (IOException ex) {
            return null;
        }
    }
}
