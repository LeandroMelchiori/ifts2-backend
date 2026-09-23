package ar.edu.ifts2.storage.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@NotNull Provider provider, boolean testEndpointsEnabled,
                                @NotNull DataSize maxImageSize, @NotNull DataSize maxDocumentSize) {
    public enum Provider { NONE, SUPABASE }

    @AssertTrue(message = "Los limites de storage deben estar entre 1 byte y 100 MB")
    public boolean isValidSizeLimits() {
        return validSize(maxImageSize) && validSize(maxDocumentSize);
    }

    @AssertTrue(message = "Los endpoints de prueba requieren un proveedor de storage habilitado")
    public boolean isValidProviderForTestEndpoints() {
        return !testEndpointsEnabled || provider != Provider.NONE;
    }

    private boolean validSize(DataSize size) {
        return size != null && size.toBytes() > 0 && size.toBytes() <= DataSize.ofMegabytes(100).toBytes();
    }
}
