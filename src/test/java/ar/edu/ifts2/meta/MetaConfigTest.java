package ar.edu.ifts2.meta;

import ar.edu.ifts2.meta.client.MetaGraphClient;
import ar.edu.ifts2.meta.config.MetaConfig;
import ar.edu.ifts2.storage.config.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MetaConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(MetaConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(StorageProperties.class, () -> new StorageProperties(StorageProperties.Provider.NONE, false,
                    DataSize.ofMegabytes(5), DataSize.ofMegabytes(10)))
            .withPropertyValues("app.meta.enabled=false", "app.meta.max-posts=25",
                    "app.meta.connect-timeout=5s", "app.meta.read-timeout=15s");

    @Test
    void disabledIntegrationStartsWithoutSecrets() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(MetaGraphClient.class));
    }

    @Test
    void enabledIntegrationRequiresExplicitCredentialsAndApiVersion() {
        runner.withPropertyValues("app.meta.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.meta.enabled=true", "app.meta.api-version=v25.0", "app.meta.account-id=123",
                "app.meta.access-token=" + UUID.randomUUID()).run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.meta.max-posts=0", "app.meta.max-posts=101", "app.meta.read-timeout=0s",
            "app.meta.read-timeout=61s", "app.meta.connect-timeout=31s"})
    void rejectsUnsafeLimits(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledIntegrationRejectsPathInjection() {
        runner.withPropertyValues("app.meta.enabled=true", "app.meta.api-version=../me", "app.meta.account-id=123",
                "app.meta.access-token=" + UUID.randomUUID()).run(context -> assertThat(context).hasFailed());
    }
}
