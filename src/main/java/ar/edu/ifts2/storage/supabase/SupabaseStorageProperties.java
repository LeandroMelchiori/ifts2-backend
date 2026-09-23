package ar.edu.ifts2.storage.supabase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage.supabase")
public record SupabaseStorageProperties(
        @NotNull URI url,
        @NotNull @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,62}") String bucket,
        String serviceRoleKey,
        @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 2) Duration connectTimeout,
        @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 2) Duration readTimeout
) {
    @Override
    public String toString() { return "SupabaseStorageProperties[REDACTED]"; }
}
