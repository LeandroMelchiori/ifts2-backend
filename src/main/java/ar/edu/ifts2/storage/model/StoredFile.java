package ar.edu.ifts2.storage.model;

public record StoredFile(String objectKey, String contentType, long size) {
}
