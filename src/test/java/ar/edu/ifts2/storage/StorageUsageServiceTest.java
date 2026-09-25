package ar.edu.ifts2.storage;

import ar.edu.ifts2.evento.repository.EventoFotoRepository;
import ar.edu.ifts2.storage.model.StorageObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageUsageServiceTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<StorageService> provider = mock(ObjectProvider.class);
    private final StorageService storage = mock(StorageService.class);
    private final EventoFotoRepository fotos = mock(EventoFotoRepository.class);

    @Test
    void countsActualObjectsIncludingOrphansAndSeparatesEventPhotosFromCovers() {
        when(provider.getIfAvailable()).thenReturn(storage);
        when(fotos.findAllObjectKeys()).thenReturn(List.of("eventos/foto.png"));
        when(storage.listObjects()).thenReturn(List.of(new StorageObject("meta/a.png", 10), new StorageObject("noticias/a.png", 20),
                new StorageObject("eventos/portada.png", 30), new StorageObject("eventos/foto.png", 40),
                new StorageObject("documentos/a.pdf", 50), new StorageObject("pruebas/orphan.png", 60)));
        var result = new StorageUsageService(provider, fotos, 1024).consultar();
        assertThat(result.usedBytes()).isEqualTo(210);
        assertThat(result.limitBytes()).isEqualTo(1024);
        assertThat(result.breakdown()).filteredOn(item -> item.key().equals("galeria"))
                .singleElement().satisfies(item -> { assertThat(item.bytes()).isEqualTo(40); assertThat(item.files()).isEqualTo(1); });
        assertThat(result.breakdown().stream().mapToLong(item -> item.bytes()).sum()).isEqualTo(result.usedBytes());
    }

    @Test
    void noConfiguredProviderIsExplicitlyUnavailable() {
        assertThatThrownBy(() -> new StorageUsageService(provider, fotos, 0).consultar())
                .isInstanceOfSatisfying(StorageException.class, ex -> assertThat(ex.getReason()).isEqualTo(StorageException.Reason.STORAGE_DISABLED));
    }

    @Test
    void providerFailureDoesNotFallBackToFakeEstimates() {
        when(provider.getIfAvailable()).thenReturn(storage);
        when(storage.listObjects()).thenThrow(new StorageException(StorageException.Reason.PROVIDER_FAILURE));
        assertThatThrownBy(() -> new StorageUsageService(provider, fotos, 0).consultar()).isInstanceOf(StorageException.class);
        verifyNoInteractions(fotos);
    }

    @Test
    void overflowingTotalsAreRejected() {
        when(provider.getIfAvailable()).thenReturn(storage);
        when(fotos.findAllObjectKeys()).thenReturn(List.of());
        when(storage.listObjects()).thenReturn(List.of(new StorageObject("meta/a", Long.MAX_VALUE), new StorageObject("meta/b", 1)));
        assertThatThrownBy(() -> new StorageUsageService(provider, fotos, 0).consultar()).isInstanceOf(StorageException.class);
    }
}
