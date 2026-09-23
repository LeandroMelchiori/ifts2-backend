package ar.edu.ifts2.storage.dto;

import java.net.URI;

public record StorageUploadResponse(String objectKey, String contentType, long size, URI publicUrl) {
}
