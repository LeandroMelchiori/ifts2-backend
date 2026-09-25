package ar.edu.ifts2.meta.client;

import ar.edu.ifts2.meta.config.MetaProperties;
import ar.edu.ifts2.meta.entity.TipoMedia;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import static ar.edu.ifts2.meta.client.MetaException.Reason.*;

public class MetaGraphClient {
    private static final String FIELDS = "id,caption,media_type,media_url,thumbnail_url,permalink,timestamp,"
            + "children.limit(1){media_type,media_url,thumbnail_url}";
    private final RestClient client;
    private final MetaProperties properties;
    private final ObjectMapper mapper;

    public MetaGraphClient(RestClient client, MetaProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<MetaMedia> recientes() {
        requireEnabled();
        Map<String, MetaMedia> posts = new LinkedHashMap<>();
        Set<String> cursors = new HashSet<>();
        String after = null;
        for (int page = 0; page < 10 && posts.size() < properties.maxPosts(); page++) {
            JsonNode response = request(properties.accountId() + "/media", after);
            JsonNode data = response.path("data");
            if (!data.isArray()) throw new MetaException(PROVIDER_FAILURE);
            for (JsonNode item : data) {
                MetaMedia media = parse(item);
                posts.put(media.id(), media);
                if (posts.size() == properties.maxPosts()) break;
            }
            if (data.isEmpty() || !response.path("paging").hasNonNull("next")) break;
            after = response.path("paging").path("cursors").path("after").asText();
            if (after.isBlank() || after.length() > 4096 || !cursors.add(after)) throw new MetaException(PROVIDER_FAILURE);
            // Se reconstruye la URL: paging.next puede incluir credenciales y no se sigue.
        }
        return List.copyOf(posts.values());
    }

    public MetaMedia obtener(String id) {
        requireEnabled();
        if (id == null || !id.matches("[0-9]{1,40}")) throw new MetaException(PROVIDER_FAILURE);
        MetaMedia media = parse(request(id, null));
        if (!id.equals(media.id())) throw new MetaException(PROVIDER_FAILURE);
        return media;
    }

    private JsonNode request(String path, String after) {
        try {
            return client.get().uri(builder -> {
                builder.path("/" + properties.apiVersion() + "/" + path).queryParam("fields", "{fields}")
                        .queryParam("limit", properties.maxPosts());
                if (after != null) builder.queryParam("after", "{after}");
                return after == null ? builder.build(FIELDS) : builder.build(FIELDS, after);
            }).headers(headers -> headers.setBearerAuth(properties.accessToken())).exchange((request, response) -> {
                byte[] bytes = response.getBody().readNBytes(1_048_577);
                if (bytes.length > 1_048_576) throw new MetaException(PROVIDER_FAILURE);
                JsonNode body;
                try { body = mapper.readTree(bytes); }
                catch (IOException ex) { throw new MetaException(PROVIDER_FAILURE); }
                if (body == null) throw new MetaException(PROVIDER_FAILURE);
                if (body.path("error").path("code").asInt() == 190) throw new MetaException(TOKEN_INVALID);
                if (!response.getStatusCode().is2xxSuccessful() || body.has("error")) throw new MetaException(PROVIDER_FAILURE);
                return body;
            });
        } catch (RestClientException ex) {
            throw new MetaException(PROVIDER_FAILURE);
        }
    }

    private MetaMedia parse(JsonNode item) {
        try {
            String id = item.path("id").asText();
            String caption = item.path("caption").asText("");
            String link = item.path("permalink").asText();
            URI permalink = URI.create(link);
            if (!id.matches("[0-9]{1,40}") || caption.length() > 10000 || link.length() > 2048
                    || !"https".equals(permalink.getScheme()) || permalink.getUserInfo() != null
                    || permalink.getPort() != -1 || !("www.instagram.com".equals(permalink.getHost())
                    || "instagram.com".equals(permalink.getHost()))) throw new IllegalArgumentException();
            TipoMedia tipo = TipoMedia.valueOf(item.path("media_type").asText());
            Instant date = date(item.path("timestamp").asText());
            JsonNode cover = tipo == TipoMedia.CAROUSEL_ALBUM && !item.path("children").path("data").isEmpty()
                    ? item.path("children").path("data").path(0) : item;
            String url = cover.path("VIDEO".equals(cover.path("media_type").asText()) ? "thumbnail_url" : "media_url").asText();
            URI preview = url.isBlank() ? null : URI.create(url);
            if (preview != null) MetaPreviewDownloader.validateUrl(preview);
            return new MetaMedia(id, caption, tipo, date, link, preview);
        } catch (IllegalArgumentException | DateTimeException ex) {
            throw new MetaException(PROVIDER_FAILURE);
        }
    }

    private Instant date(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException ex) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")).toInstant();
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new MetaException(DISABLED);
    }
}
