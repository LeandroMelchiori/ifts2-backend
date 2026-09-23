package ar.edu.ifts2.storage.controller;

import ar.edu.ifts2.shared.error.ApiError;
import ar.edu.ifts2.storage.StorageException;
import ar.edu.ifts2.storage.StorageService;
import ar.edu.ifts2.storage.dto.StorageUploadResponse;
import ar.edu.ifts2.storage.dto.StorageUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/admin/storage/test")
@ConditionalOnProperty(prefix = "app.storage", name = "test-endpoints-enabled", havingValue = "true")
@Tag(name = "Storage", description = "Endpoints TEMPORALES de verificacion; solo namespace pruebas; ADMIN o EDITOR")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Archivo, firma binaria o objectKey invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "502", description = "Fallo del proveedor de storage", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class StorageTestController {
    private static final String TEST_KEY = "pruebas/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)";
    private final StorageService storage;

    public StorageTestController(StorageService storage) { this.storage = storage; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "TEMPORAL: subir archivo de prueba", description = "JPEG, PNG, WEBP (5 MB por defecto) o PDF (10 MB). Valida MIME y firma binaria. Ignora el nombre original y genera pruebas/{uuid}.extension.")
    @ApiResponse(responseCode = "201", description = "Archivo subido; persistir objectKey, no publicUrl")
    @ApiResponse(responseCode = "413", description = "Archivo o request demasiado grande", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "Tipo MIME no permitido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<StorageUploadResponse> upload(
            @Parameter(description = "Archivo binario permitido", required = true)
            @RequestPart("file") MultipartFile file) {
        try (var stream = file.getInputStream()) {
            var stored = storage.upload("pruebas", file.getContentType(), file.getSize(), stream);
            var url = storage.resolvePublicUrl(stored.objectKey());
            return ResponseEntity.created(url).body(
                    new StorageUploadResponse(stored.objectKey(), stored.contentType(), stored.size(), url));
        } catch (IOException ex) {
            throw new StorageException(StorageException.Reason.INVALID_FILE);
        }
    }

    @DeleteMapping
    @Operation(summary = "TEMPORAL: eliminar archivo de prueba", description = "Solo acepta objectKey generados bajo pruebas/. No permite borrar archivos de otros namespaces.")
    @ApiResponse(responseCode = "204", description = "Objeto eliminado", content = @Content)
    @ApiResponse(responseCode = "404", description = "Objeto inexistente", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> delete(@Parameter(description = "Clave devuelta por upload")
                                      @RequestParam @Pattern(regexp = TEST_KEY) String objectKey) {
        storage.delete(objectKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/url")
    @Operation(summary = "TEMPORAL: resolver URL publica", description = "Construye la URL del bucket publico; no comprueba la existencia del objeto. La identidad sigue siendo objectKey.")
    @ApiResponse(responseCode = "200", description = "URL publica derivada de objectKey")
    public StorageUrlResponse resolve(@Parameter(description = "Clave devuelta por upload")
                                      @RequestParam @Pattern(regexp = TEST_KEY) String objectKey) {
        return new StorageUrlResponse(objectKey, storage.resolvePublicUrl(objectKey));
    }
}
