package ar.edu.ifts2.storage;

import ar.edu.ifts2.storage.config.StorageProperties;
import ar.edu.ifts2.storage.validation.FileValidator;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import static ar.edu.ifts2.storage.StorageException.Reason.*;
import static org.assertj.core.api.Assertions.*;

class FileValidatorTest {
    private final FileValidator validator = new FileValidator(new StorageProperties(StorageProperties.Provider.NONE,
            false, DataSize.ofBytes(1024), DataSize.ofBytes(2048)));
    static final byte[] PNG = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Test
    void supportsAllowedSignaturesAndGeneratesUuidKeys() {
        check("image/png", PNG, "png");
        check("image/jpeg", new byte[]{(byte) 255, (byte) 216, (byte) 255, 0}, "jpg");
        check("image/webp", "RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), "webp");
        check("application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), "pdf");
    }

    @Test
    void rejectsMimeSpoofing() {
        expect(INVALID_FILE, () -> validator.validate("noticias", "image/jpeg", PNG.length, stream(PNG)));
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        expect(INVALID_FILE, () -> validator.validate("documentos", "application/pdf", html.length, stream(html)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/svg+xml", "text/html", "application/octet-stream", "", "application/zip"})
    void rejectsUnsupportedMimeTypes(String mime) {
        expect(UNSUPPORTED_TYPE, () -> validator.validate("pruebas", mime, PNG.length, stream(PNG)));
    }

    @Test
    void rejectsMissingMimeAndEmptyFiles() {
        expect(UNSUPPORTED_TYPE, () -> validator.validate("pruebas", null, PNG.length, stream(PNG)));
        expect(INVALID_FILE, () -> validator.validate("pruebas", "image/png", 0, stream(new byte[0])));
    }

    @Test
    void checksDeclaredAndActualSizes() {
        expect(TOO_LARGE, () -> validator.validate("pruebas", "image/png", 1025, stream(PNG)));
        expect(TOO_LARGE, () -> validator.validate("pruebas", "image/png", 8, stream(Arrays.copyOf(PNG, 1025))));
        expect(INVALID_FILE, () -> validator.validate("pruebas", "image/png", 9, stream(PNG)));
        byte[] pdf = Arrays.copyOf("%PDF-".getBytes(StandardCharsets.US_ASCII), 1500);
        assertThat(validator.validate("documentos", "application/pdf", pdf.length, stream(pdf)).bytes()).hasSize(1500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../pruebas", "../../archivo", "/pruebas", "pruebas/../noticias", "pruebas%2fnoticias", "pruebas\\noticias", "pruebas//imagen", "https://external.test"})
    void rejectsUnsafeNamespaces(String namespace) {
        expect(INVALID_FILE, () -> validator.validate(namespace, "image/png", PNG.length, stream(PNG)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../archivo", "pruebas/../file.pdf", "https://external.test/file.pdf", "/pruebas/file.pdf", "pruebas/file.pdf", "pruebas/%2e%2e/file.pdf"})
    void rejectsUnsafeObjectKeys(String key) {
        expect(INVALID_FILE, () -> validator.validateObjectKey(key));
    }

    @Test
    void handlesUnreadableInputWithoutLeakingMessage() {
        var broken = new java.io.InputStream() {
            @Override public int read() throws java.io.IOException { throw new java.io.IOException("internal details"); }
        };
        expect(INVALID_FILE, () -> validator.validate("pruebas", "image/png", 8, broken));
    }

    private void check(String mime, byte[] bytes, String extension) {
        var result = validator.validate("institucion/imagenes", mime, bytes.length, stream(bytes));
        assertThat(result.objectKey()).startsWith("institucion/imagenes/").endsWith("." + extension);
        String generated = result.objectKey().substring("institucion/imagenes/".length()).split("\\.")[0];
        assertThatCode(() -> UUID.fromString(generated)).doesNotThrowAnyException();
        validator.validateObjectKey(result.objectKey());
        assertThat(result.contentType()).isEqualTo(mime);
    }

    private ByteArrayInputStream stream(byte[] bytes) { return new ByteArrayInputStream(bytes); }

    private void expect(StorageException.Reason reason, ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(StorageException.class,
                ex -> { assertThat(ex.getReason()).isEqualTo(reason); assertThat(ex.getCause()).isNull(); });
    }
}
