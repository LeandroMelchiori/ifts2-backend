package ar.edu.ifts2.storage.validation;

import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.config.StorageProperties;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import static ar.edu.ifts2.storage.StorageException.Reason.*;

@Component
public class FileValidator {
    private static final String NAMESPACE = "[a-z][a-z0-9-]{0,39}(?:/[a-z][a-z0-9-]{0,39}){0,3}";
    private static final Pattern KEY = Pattern.compile(NAMESPACE
            + "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)");
    private final StorageProperties properties;

    public FileValidator(StorageProperties properties) { this.properties = properties; }

    public ValidatedFile validate(String namespace, String contentType, long size, InputStream content) {
        if (namespace == null || !namespace.matches(NAMESPACE) || content == null || size <= 0) {
            throw new StorageException(INVALID_FILE);
        }
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).strip();
        String extension = switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> throw new StorageException(UNSUPPORTED_TYPE);
        };
        long limit = (mime.equals("application/pdf") ? properties.maxDocumentSize() : properties.maxImageSize()).toBytes();
        if (size > limit) throw new StorageException(TOO_LARGE);
        byte[] bytes;
        try {
            // Limitar tambien la lectura real: el tamano declarado no es una garantia.
            bytes = content.readNBytes(Math.toIntExact(limit + 1));
        } catch (IOException ex) {
            throw new StorageException(INVALID_FILE);
        }
        if (bytes.length > limit) throw new StorageException(TOO_LARGE);
        if (bytes.length != size || !matchesSignature(mime, bytes)) throw new StorageException(INVALID_FILE);
        return new ValidatedFile(namespace + "/" + UUID.randomUUID() + "." + extension, mime, bytes);
    }

    public void validateObjectKey(String objectKey) {
        if (objectKey == null || !KEY.matcher(objectKey).matches()) throw new StorageException(INVALID_FILE);
    }

    public ValidatedFile validateImage(String namespace, String contentType, long size, InputStream content) {
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).strip();
        if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp")) {
            throw new StorageException(UNSUPPORTED_TYPE);
        }
        return validate(namespace, mime, size, content);
    }

    private boolean matchesSignature(String mime, byte[] bytes) {
        return switch (mime) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 255
                    && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 255) == 137
                    && startsAt(bytes, 1, "PNG\r\n\u001a\n");
            case "image/webp" -> bytes.length >= 16 && startsAt(bytes, 0, "RIFF") && startsAt(bytes, 8, "WEBP")
                    && (startsAt(bytes, 12, "VP8 ") || startsAt(bytes, 12, "VP8L") || startsAt(bytes, 12, "VP8X"));
            case "application/pdf" -> startsAt(bytes, 0, "%PDF-");
            default -> false;
        };
    }

    private boolean startsAt(byte[] bytes, int offset, String signature) {
        byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) return false;
        }
        return true;
    }

    public record ValidatedFile(String objectKey, String contentType, byte[] bytes) {
    }
}
