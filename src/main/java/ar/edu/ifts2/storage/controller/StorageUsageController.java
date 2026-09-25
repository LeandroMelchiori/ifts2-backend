package ar.edu.ifts2.storage.controller;

import ar.edu.ifts2.storage.StorageUsageService;
import ar.edu.ifts2.storage.dto.StorageUsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Storage")
@SecurityRequirement(name = "bearerAuth")
public class StorageUsageController {
    private final StorageUsageService service;
    public StorageUsageController(StorageUsageService service) { this.service = service; }

    @GetMapping("/api/admin/storage/usage")
    @Operation(summary = "Consultar bytes del bucket institucional",
            description = "ADMIN o EDITOR. Consulta metadatos reales del proveedor, no estima por cantidad de fotos. limitBytes es un presupuesto configurable, null si no se definio; no representa la cuota total de Supabase. Medicion no atomica ante cargas concurrentes. 503 si no disponible; nunca devuelve cero inventado.")
    public StorageUsageResponse consultar() { return service.consultar(); }
}
