package ar.edu.ifts2.noticia.controller;

import ar.edu.ifts2.noticia.dto.NoticiaPortadaResponse;
import ar.edu.ifts2.noticia.service.NoticiaPortadaService;
import ar.edu.ifts2.shared.error.ApiError;
import ar.edu.ifts2.storage.StorageException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/noticias/{id}/portada")
@Tag(name = "News Administration")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "UUID, archivo o firma binaria invalidos",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Noticia inexistente",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Storage deshabilitado",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class NoticiaPortadaController {
    private final NoticiaPortadaService service;

    public NoticiaPortadaController(NoticiaPortadaService service) { this.service = service; }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir o reemplazar portada", description = "JPEG, PNG o WEBP; valida MIME, firma y tamano (5 MB por defecto). Genera noticias/{uuid}.extension sin usar el nombre original. Conserva estado editorial. Borra la anterior despues del commit; los fallos de limpieza se registran para revision manual.")
    @ApiResponse(responseCode = "200", description = "Portada asociada; objectKey es su identidad y publicUrl es derivada")
    @ApiResponse(responseCode = "413", description = "Imagen o request demasiado grande", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "MIME no permitido; PDF y SVG no se aceptan", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Fallo al subir la imagen; se conserva la portada anterior", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public NoticiaPortadaResponse subir(@PathVariable UUID id,
            @Parameter(description = "Imagen de portada", required = true) @RequestPart("file") MultipartFile file) {
        try (var stream = file.getInputStream()) {
            return service.subir(id, file.getContentType(), file.getSize(), stream);
        } catch (IOException ex) {
            throw new StorageException(StorageException.Reason.INVALID_FILE);
        }
    }

    @DeleteMapping
    @Operation(summary = "Quitar portada", description = "Desasocia la portada sin borrar la noticia ni cambiar su estado. Idempotente si no hay portada. El archivo se borra despues del commit; si falla, se registra su clave para limpieza manual.")
    @ApiResponse(responseCode = "204", description = "Noticia sin portada", content = @Content)
    public ResponseEntity<Void> quitar(@PathVariable UUID id) {
        service.quitar(id);
        return ResponseEntity.noContent().build();
    }
}
