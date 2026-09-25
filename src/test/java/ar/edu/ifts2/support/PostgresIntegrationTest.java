package ar.edu.ifts2.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost/unused", "spring.datasource.username=unused",
        "spring.datasource.password=", "app.bootstrap-admin.enabled=false", "app.jwt.issuer=ifts2-test",
        "app.jwt.access-token-ttl=15m", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
        "app.storage.provider=none", "app.storage.test-endpoints-enabled=false", "app.meta.enabled=false",
        "app.storage.usage-limit-bytes=0",
        "app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://localhost:3000"
})
@AutoConfigureMockMvc
@Import(PostgresIntegrationTest.DatabaseConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {
    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String encoded = Base64.getEncoder().encodeToString(secret);
        registry.add("app.jwt.secret", () -> encoded);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class DatabaseConfiguration {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres postgres() throws IOException {
            return EmbeddedPostgres.builder().setPort(0).start();
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }
    }
}
