package ar.edu.ifts2.storage.dto;

import java.net.URI;

public record StorageUrlResponse(String objectKey, URI publicUrl) {
}
