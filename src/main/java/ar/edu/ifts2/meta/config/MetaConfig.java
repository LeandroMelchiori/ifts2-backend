package ar.edu.ifts2.meta.config;

import ar.edu.ifts2.meta.client.*;
import ar.edu.ifts2.storage.config.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MetaProperties.class)
public class MetaConfig {
    @Bean
    MetaGraphClient metaGraphClient(MetaProperties properties, ObjectMapper mapper) {
        return new MetaGraphClient(RestClient.builder().baseUrl("https://graph.facebook.com").requestFactory(factory(properties)).build(),
                properties, mapper);
    }

    @Bean
    MetaPreviewDownloader metaPreviewDownloader(MetaProperties properties, StorageProperties storage) {
        // Cliente separado sin Authorization; nunca enviar el token de Graph al CDN.
        return new MetaPreviewDownloader(RestClient.builder().requestFactory(factory(properties)).build(),
                storage.maxImageSize().toBytes());
    }

    private JdkClientHttpRequestFactory factory(MetaProperties properties) {
        var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
