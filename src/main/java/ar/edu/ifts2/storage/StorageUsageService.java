package ar.edu.ifts2.storage;

import ar.edu.ifts2.evento.repository.EventoFotoRepository;
import ar.edu.ifts2.storage.dto.StorageUsageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class StorageUsageService {
    private final ObjectProvider<StorageService> provider;
    private final EventoFotoRepository fotos;
    private final Long limit;

    public StorageUsageService(ObjectProvider<StorageService> provider, EventoFotoRepository fotos,
            @Value("${app.storage.usage-limit-bytes:0}") long limit) {
        if (limit < 0) throw new IllegalArgumentException("El presupuesto de storage no puede ser negativo");
        this.provider = provider;
        this.fotos = fotos;
        this.limit = limit == 0 ? null : limit;
    }

    public StorageUsageResponse consultar() {
        StorageService storage = provider.getIfAvailable();
        if (storage == null) throw new StorageException(StorageException.Reason.STORAGE_DISABLED);
        var objects = storage.listObjects();
        Set<String> galleryKeys = new HashSet<>(fotos.findAllObjectKeys());
        Map<String, long[]> totals = new LinkedHashMap<>();
        for (String key : List.of("meta", "noticias", "eventos", "galeria", "documentos", "otros")) totals.put(key, new long[2]);
        long used = 0;
        try {
            for (var object : objects) {
                if (object.size() < 0) throw new StorageException(StorageException.Reason.PROVIDER_FAILURE);
                String key = object.objectKey();
                String group = galleryKeys.contains(key) ? "galeria" :
                        List.of("meta", "noticias", "eventos", "documentos").stream()
                                .filter(prefix -> key.startsWith(prefix + "/")).findFirst().orElse("otros");
                long[] count = totals.get(group);
                count[0] = Math.addExact(count[0], object.size());
                count[1]++;
                used = Math.addExact(used, object.size());
            }
        } catch (ArithmeticException ex) {
            throw new StorageException(StorageException.Reason.PROVIDER_FAILURE);
        }
        Map<String, String> labels = Map.of("meta", "Instagram / Meta", "noticias", "Portadas noticias",
                "eventos", "Archivos eventos", "galeria", "Fotos galeria", "documentos", "Documentos", "otros", "Otros archivos");
        var breakdown = totals.entrySet().stream().map(entry -> new StorageUsageResponse.Breakdown(
                entry.getKey(), labels.get(entry.getKey()), entry.getValue()[0], entry.getValue()[1])).toList();
        return new StorageUsageResponse(used, limit, "BUCKET", Instant.now(), breakdown);
    }
}
