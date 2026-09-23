package ar.edu.ifts2.storage.supabase;

import ar.edu.ifts2.storage.config.StorageProperties;
import ar.edu.ifts2.storage.validation.FileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseStorageConfigTest {
    private final SupabaseStorageConfig config = new SupabaseStorageConfig();
    private final FileValidator validator = new FileValidator(new StorageProperties(StorageProperties.Provider.SUPABASE,
            true, DataSize.ofMegabytes(5), DataSize.ofMegabytes(10)));

    @Test
    void privilegedHeadersAreConfiguredOnlyOnBackendClient() {
        String key = UUID.randomUUID().toString();
        RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(mock(RestClient.class));
        config.storageService(builder, properties("https://storage.example.invalid", key), validator, new ObjectMapper());
        verify(builder).defaultHeader("apikey", key);
        verify(builder).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key);
        assertThat(properties("https://storage.example.invalid", key).toString()).doesNotContain(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://storage.example.invalid", "https://user:password@storage.example.invalid", "https://storage.example.invalid/path", "https://storage.example.invalid?key=value"})
    void rejectsNonRootOrInsecureUrls(String url) {
        assertThatThrownBy(() -> config.storageService(RestClient.builder(), properties(url, UUID.randomUUID().toString()),
                validator, new ObjectMapper())).isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void rejectsMissingPrivilegedCredential() {
        assertThatThrownBy(() -> config.storageService(RestClient.builder(), properties("https://storage.example.invalid", ""),
                validator, new ObjectMapper())).isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    private SupabaseStorageProperties properties(String url, String key) {
        return new SupabaseStorageProperties(URI.create(url), "institutional", key, Duration.ofSeconds(5), Duration.ofSeconds(15));
    }
}
