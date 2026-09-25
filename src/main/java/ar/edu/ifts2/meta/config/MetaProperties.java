package ar.edu.ifts2.meta.config;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.meta")
public record MetaProperties(boolean enabled, String apiVersion, String accountId,
        String accessToken, @Min(1) @Max(100) int maxPosts,
        @NotNull @DurationMin(seconds = 1) @DurationMax(seconds = 30) Duration connectTimeout,
        @NotNull @DurationMin(seconds = 1) @DurationMax(seconds = 60) Duration readTimeout) {
    @AssertTrue(message = "Meta requiere version vN.N, ID de cuenta numerico y token de servidor")
    public boolean isConfiguredWhenEnabled() {
        return !enabled || (apiVersion != null && apiVersion.matches("v[0-9]{1,3}\\.[0-9]{1,2}")
                && accountId != null && accountId.matches("[0-9]{1,40}")
                && accessToken != null && accessToken.matches("[A-Za-z0-9._~-]{10,4096}"));
    }

    @Override
    public String toString() { return "MetaProperties[REDACTED]"; }
}
