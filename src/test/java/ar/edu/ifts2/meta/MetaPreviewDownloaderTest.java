package ar.edu.ifts2.meta;

import ar.edu.ifts2.meta.client.*;
import ar.edu.ifts2.storage.StorageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.URI;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MetaPreviewDownloaderTest {
    private MockRestServiceServer server;
    private MetaPreviewDownloader downloader;
    private final URI url = URI.create("https://scontent.cdninstagram.com/image.jpg?signature=example");

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        downloader = new MetaPreviewDownloader(builder.build(), 8);
    }
    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void cdnRequestHasNoGraphCredentials() {
        server.expect(requestTo(url)).andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));
        assertThat(downloader.descargar(url).bytes()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://scontent.cdninstagram.com/x", "https://127.0.0.1/x", "https://[::1]/x",
            "https://169.254.169.254/x", "https://example.com/x", "https://scontent.cdninstagram.com.evil.test/x",
            "https://user:pass@scontent.cdninstagram.com/x", "https://scontent.cdninstagram.com:8443/x",
            "https://scontent.cdninstagram.com/x#fragment", "file:///tmp/x"})
    void unsafeUrlsNeverReachNetwork(String value) {
        assertThatThrownBy(() -> downloader.descargar(URI.create(value))).isInstanceOf(MetaException.class);
    }

    @Test
    void rejectsRedirectWithoutFollowingLocation() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.FOUND).location(URI.create("https://127.0.0.1/private")));
        assertThatThrownBy(() -> downloader.descargar(url)).isInstanceOf(MetaException.class);
    }

    @Test
    void streamReadIsBoundedEvenWithoutContentLength() {
        server.expect(requestTo(url)).andRespond(withSuccess(new byte[9], MediaType.IMAGE_JPEG));
        assertThatThrownBy(() -> downloader.descargar(url)).isInstanceOfSatisfying(StorageException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(StorageException.Reason.TOO_LARGE));
    }

    @Test
    void missingPreviewIsExplicitConflict() {
        assertThatThrownBy(() -> downloader.descargar(null)).isInstanceOfSatisfying(MetaException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(MetaException.Reason.PREVIEW_MISSING));
    }
}
