package ar.edu.ifts2.storage;

import ar.edu.ifts2.storage.config.StorageProperties;
import ar.edu.ifts2.storage.supabase.SupabaseStorageProperties;
import ar.edu.ifts2.storage.supabase.SupabaseStorageService;
import ar.edu.ifts2.storage.validation.FileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import static ar.edu.ifts2.storage.StorageException.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseStorageServiceTest {
    private final String secret = UUID.randomUUID().toString();
    private final String base = "https://storage.example.invalid";
    private final String key = "pruebas/" + UUID.randomUUID() + ".png";
    private MockRestServiceServer server;
    private StorageService service;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl(base).defaultHeader("apikey", secret)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new SupabaseStorageProperties(URI.create(base), "institutional", secret,
                Duration.ofSeconds(5), Duration.ofSeconds(15));
        var validator = new FileValidator(new StorageProperties(StorageProperties.Provider.SUPABASE, true,
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(10)));
        service = new SupabaseStorageService(builder.build(), properties, validator, new ObjectMapper());
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void uploadUsesDocumentedRawHttpContract() {
        server.expect(requestTo(startsWith(base + "/storage/v1/object/institutional/noticias/")))
                .andExpect(method(HttpMethod.POST)).andExpect(header("apikey", secret))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + secret))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(FileValidatorTest.PNG))
                .andRespond(withSuccess("{\"Key\":\"provider-specific\"}", MediaType.APPLICATION_JSON));
        var stored = upload();
        assertThat(stored.objectKey()).startsWith("noticias/").endsWith(".png").doesNotContain("https:", "institutional");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.size()).isEqualTo(FileValidatorTest.PNG.length);
    }

    @Test
    void deleteUsesPrefixesAndExactObjectName() {
        server.expect(requestTo(base + "/storage/v1/object/institutional")).andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("{\"prefixes\":[\"" + key + "\"]}"))
                .andRespond(withSuccess("[{\"name\":\"" + key + "\"}]", MediaType.APPLICATION_JSON));
        service.delete(key);
    }

    @Test
    void emptyDeleteResultMeansNotFound() {
        server.expect(requestTo(base + "/storage/v1/object/institutional"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        expect(NOT_FOUND, () -> service.delete(key));
    }

    @Test
    void noSuchKeyIsTranslatedEvenWithLegacy400Status() {
        server.expect(requestTo(base + "/storage/v1/object/institutional"))
                .andRespond(withBadRequest().body("{\"code\":\"NoSuchKey\",\"message\":\"private details\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        expect(NOT_FOUND, () -> service.delete(key));
    }

    @Test
    void missingBucketIsProviderErrorNotMissingObject() {
        server.expect(requestTo(base + "/storage/v1/object/institutional"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"NoSuchBucket\"}").contentType(MediaType.APPLICATION_JSON));
        expect(PROVIDER_FAILURE, () -> service.delete(key));
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 401, 403, 404, 429, 500, 503})
    void providerFailuresNeverLeakResponseOrSecret(int status) {
        server.expect(requestTo(startsWith(base + "/storage/v1/object/institutional/noticias/")))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"message\":\"" + secret + "\"}")
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(this::upload).isInstanceOfSatisfying(StorageException.class, ex -> {
            assertThat(ex.getReason()).isEqualTo(PROVIDER_FAILURE);
            assertThat(ex.getMessage()).doesNotContain(secret, base);
            assertThat(ex.getCause()).isNull();
        });
    }

    @Test
    void translatesProviderSizeLimit() {
        server.expect(requestTo(startsWith(base + "/storage/v1/object/institutional/noticias/")))
                .andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE));
        expect(TOO_LARGE, this::upload);
    }

    @Test
    void translatesProviderMimeLimit() {
        server.expect(requestTo(startsWith(base + "/storage/v1/object/institutional/noticias/")))
                .andRespond(withBadRequest().body("{\"code\":\"InvalidMimeType\"}").contentType(MediaType.APPLICATION_JSON));
        expect(UNSUPPORTED_TYPE, this::upload);
    }

    @Test
    void translatesNetworkFailureWithoutCause() {
        server.expect(requestTo(startsWith(base + "/storage/v1/object/institutional/noticias/")))
                .andRespond(withException(new IOException(secret)));
        expect(PROVIDER_FAILURE, this::upload);
    }

    @Test
    void rejectsMalformedSuccessResponse() {
        server.expect(requestTo(base + "/storage/v1/object/institutional"))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));
        expect(PROVIDER_FAILURE, () -> service.delete(key));
    }

    @Test
    void invalidKeysNeverReachProvider() {
        expect(INVALID_FILE, () -> service.delete("../../private"));
        expect(INVALID_FILE, () -> service.resolvePublicUrl("https://external.test"));
    }

    @Test
    void publicUrlIsDerivedWithoutHttpRequest() {
        assertThat(service.resolvePublicUrl(key)).isEqualTo(URI.create(base + "/storage/v1/object/public/institutional/" + key));
    }

    private ar.edu.ifts2.storage.model.StoredFile upload() {
        return service.upload("noticias", "image/png", FileValidatorTest.PNG.length, new ByteArrayInputStream(FileValidatorTest.PNG));
    }

    private void expect(StorageException.Reason reason, ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(StorageException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(reason));
    }
}
