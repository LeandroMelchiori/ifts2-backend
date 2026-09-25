package ar.edu.ifts2.meta;

import ar.edu.ifts2.meta.client.*;
import ar.edu.ifts2.meta.config.MetaProperties;
import ar.edu.ifts2.meta.entity.TipoMedia;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MetaGraphClientTest {
    private final String token = UUID.randomUUID().toString();
    private MockRestServiceServer server;
    private MetaGraphClient client;
    private RestClient.Builder builder;

    @BeforeEach
    void setup() {
        builder = RestClient.builder().baseUrl("https://graph.facebook.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MetaGraphClient(builder.build(), properties(true, 25), new ObjectMapper());
    }
    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void sendsBearerOnlyToFixedGraphOriginAndParsesMedia() {
        server.expect(requestTo(containsString("https://graph.facebook.com/v25.0/123/media?")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain(token, "access_token="))
                .andRespond(withSuccess("{\"data\":[" + media("1", "IMAGE", "2026-09-20T12:00:00+0000") + "]}", MediaType.APPLICATION_JSON));
        var result = client.recientes();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tipo()).isEqualTo(TipoMedia.IMAGE);
        assertThat(result.getFirst().fecha()).isEqualTo(Instant.parse("2026-09-20T12:00:00Z"));
        assertThat(result.getFirst().preview().getHost()).isEqualTo("scontent.cdninstagram.com");
    }

    @Test
    void followsCursorButNeverUntrustedNextUrl() {
        server.expect(requestTo(containsString("/123/media?"))).andRespond(withSuccess(
                "{\"data\":[" + media("1", "IMAGE", "2026-09-20T12:00:00Z") + "],\"paging\":{\"next\":\"https://evil.test/?access_token=private\",\"cursors\":{\"after\":\"cursor&x=1\"}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://graph.facebook.com/v25.0/123/media?")))
                .andExpect(requestTo(containsString("after=cursor%26x%3D1")))
                .andRespond(withSuccess("{\"data\":[" + media("2", "VIDEO", "2026-09-19T12:00:00Z") + "]}", MediaType.APPLICATION_JSON));
        var result = client.recientes();
        assertThat(result).hasSize(2);
        assertThat(result.get(1).preview().getPath()).isEqualTo("/preview.jpg");
    }

    @Test
    void albumUsesFirstChildPreviewAndNeverDownloadsVideoUrl() {
        String album = """
                {"id":"1","caption":"Album","media_type":"CAROUSEL_ALBUM","timestamp":"2026-09-20T12:00:00Z",
                 "permalink":"https://www.instagram.com/p/1/",
                 "children":{"data":[{"media_type":"VIDEO","media_url":"https://scontent.cdninstagram.com/video.mp4",
                 "thumbnail_url":"https://scontent.cdninstagram.com/thumb.jpg"}]}}
                """;
        server.expect(requestTo(containsString("/v25.0/1?"))).andRespond(withSuccess(album, MediaType.APPLICATION_JSON));
        var result = client.obtener("1");
        assertThat(result.tipo()).isEqualTo(TipoMedia.CAROUSEL_ALBUM);
        assertThat(result.preview().getPath()).isEqualTo("/thumb.jpg");
    }

    @Test
    void missingPreviewIsRepresentedWithoutDiscardingPost() {
        String json = media("1", "IMAGE", "2026-09-20T12:00:00Z").replace("https://scontent.cdninstagram.com/image.jpg", "");
        server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThat(client.obtener("1").preview()).isNull();
    }

    @Test
    void maximumPostsBoundsImportAndStopsPaging() {
        client = new MetaGraphClient(builder.build(), properties(true, 1), new ObjectMapper());
        server.expect(anything()).andRespond(withSuccess("{\"data\":[" + media("1", "IMAGE", "2026-09-20T12:00:00Z")
                + "],\"paging\":{\"next\":\"ignored\",\"cursors\":{\"after\":\"second\"}}}", MediaType.APPLICATION_JSON));
        assertThat(client.recientes()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 401, 403, 429, 500, 503})
    void upstreamFailuresDoNotExposeCredentials(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("{\"error\":{\"message\":\"" + token + "\"}}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::recientes).isInstanceOfSatisfying(MetaException.class, ex -> {
            assertThat(ex.getMessage()).doesNotContain(token, "https");
            assertThat(ex.getCause()).isNull();
            assertThat(ex.getReason()).isEqualTo(MetaException.Reason.PROVIDER_FAILURE);
        });
    }

    @Test
    void invalidTokenHasActionableSafeError() {
        server.expect(anything()).andRespond(withBadRequest().body("{\"error\":{\"code\":190,\"message\":\"" + token + "\"}}"));
        assertThatThrownBy(client::recientes).isInstanceOfSatisfying(MetaException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(MetaException.Reason.TOKEN_INVALID));
    }

    @Test
    void networkFailureHasNoSensitiveCause() {
        server.expect(anything()).andRespond(withException(new IOException(token)));
        assertThatThrownBy(client::recientes).isInstanceOfSatisfying(MetaException.class, ex -> assertThat(ex.getCause()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null", "{}", "{\"data\":{}}", "{\"data\":[{}]}"})
    void malformedResponsesAreRejected(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::recientes).isInstanceOf(MetaException.class);
    }

    @Test
    void oversizedGraphResponseIsBounded() {
        server.expect(anything()).andRespond(withSuccess(" ".repeat(1_048_577), MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::recientes).isInstanceOf(MetaException.class);
    }

    @Test
    void unexpectedIdIsRejected() {
        server.expect(anything()).andRespond(withSuccess(media("2", "IMAGE", "2026-09-20T12:00:00Z"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.obtener("1")).isInstanceOf(MetaException.class);
    }

    @Test
    void disabledClientNeverCallsNetworkAndPropertiesAreRedacted() {
        client = new MetaGraphClient(builder.build(), properties(false, 25), new ObjectMapper());
        assertThatThrownBy(client::recientes).isInstanceOfSatisfying(MetaException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(MetaException.Reason.DISABLED));
        assertThat(properties(true, 25).toString()).doesNotContain(token);
    }

    private MetaProperties properties(boolean enabled, int limit) {
        return new MetaProperties(enabled, "v25.0", "123", token, limit,
                Duration.ofSeconds(5), Duration.ofSeconds(15));
    }

    private String media(String id, String type, String date) {
        return """
                {"id":"%s","caption":"Texto","media_type":"%s","timestamp":"%s",
                 "permalink":"https://www.instagram.com/p/%s/",
                 "media_url":"https://scontent.cdninstagram.com/image.jpg",
                 "thumbnail_url":"https://scontent.cdninstagram.com/preview.jpg"}
                """.formatted(id, type, date, id);
    }
}
