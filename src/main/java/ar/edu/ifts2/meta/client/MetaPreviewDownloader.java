package ar.edu.ifts2.meta.client;

import ar.edu.ifts2.storage.StorageException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.net.URI;
import java.util.Locale;
import static ar.edu.ifts2.meta.client.MetaException.Reason.*;

public class MetaPreviewDownloader {
    private final RestClient client;
    private final long maxBytes;
    public MetaPreviewDownloader(RestClient client, long maxBytes) {
        this.client = client;
        this.maxBytes = maxBytes;
    }

    public Preview descargar(URI url) {
        if (url == null) throw new MetaException(PREVIEW_MISSING);
        validateUrl(url);
        try {
            return client.get().uri(url).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) throw new MetaException(PROVIDER_FAILURE);
                if (response.getHeaders().getContentLength() > maxBytes) throw tooLarge();
                byte[] bytes = response.getBody().readNBytes(Math.toIntExact(maxBytes + 1));
                if (bytes.length > maxBytes) throw tooLarge();
                var type = response.getHeaders().getContentType();
                return new Preview(type == null ? "" : type.getType() + "/" + type.getSubtype(), bytes);
            });
        } catch (RestClientException ex) {
            throw new MetaException(PROVIDER_FAILURE);
        }
    }

    static void validateUrl(URI url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        boolean trusted = host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net");
        if (!"https".equals(url.getScheme()) || !trusted || url.getUserInfo() != null
                || url.getFragment() != null || (url.getPort() != -1 && url.getPort() != 443)
                || url.toString().length() > 8192) throw new MetaException(PROVIDER_FAILURE);
    }

    private StorageException tooLarge() { return new StorageException(StorageException.Reason.TOO_LARGE); }
    public record Preview(String contentType, byte[] bytes) { }
}
