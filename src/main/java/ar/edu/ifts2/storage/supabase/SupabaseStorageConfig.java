package ar.edu.ifts2.storage.supabase;

import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.validation.FileValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "supabase")
@EnableConfigurationProperties(SupabaseStorageProperties.class)
public class SupabaseStorageConfig {
    @Bean
    StorageService storageService(RestClient.Builder builder, SupabaseStorageProperties properties,
                                  FileValidator validator, ObjectMapper mapper) {
        URI url = properties.url();
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                || url.getQuery() != null || url.getFragment() != null
                || !(url.getPath().isEmpty() || url.getPath().equals("/"))) {
            throw new IllegalStateException("SUPABASE_URL debe ser la URL HTTPS raiz del proyecto, sin credenciales");
        }
        String key = properties.serviceRoleKey();
        if (key == null || key.isBlank() || !key.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("SUPABASE_SERVICE_ROLE_KEY es obligatoria y debe ser una credencial valida");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        RestClient restClient = builder.baseUrl(url.toString()).requestFactory(factory)
                .defaultHeader("apikey", key).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key).build();
        return new SupabaseStorageService(restClient, properties, validator, mapper);
    }
}
