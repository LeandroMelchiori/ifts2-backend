package ar.edu.ifts2.evento.controller;

import ar.edu.ifts2.evento.dto.*;
import ar.edu.ifts2.evento.service.EventoFotoService;
import ar.edu.ifts2.shared.error.ApiError;
import ar.edu.ifts2.storage.StorageException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/admin/eventos/{eventoId}/fotos")
@Tag(name = "Eventos Administration")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Datos o archivo invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Evento o foto inexistentes", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Limite de 50 fotos alcanzado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "413", description = "Archivo demasiado grande", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "415", description = "MIME no permitido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "502", description = "Fallo del proveedor de almacenamiento", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Storage deshabilitado", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class EventoFotoAdminController {
    private final EventoFotoService service;
    public EventoFotoAdminController(EventoFotoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar fotos del evento", description = "Orden: orden e id ascendentes. Incluye todos los estados del evento; maximo 50.")
    public List<EventoFotoAdminResponse> listar(@PathVariable UUID eventoId) { return service.listarAdmin(eventoId); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Agregar foto al evento",
            description = "Multipart con file (JPEG, PNG o WEBP) y datos (application/json: etiqueta y orden). Reutiliza los limites y validacion del storage. No cambia la portada ni el estado del evento.")
    @ApiResponse(responseCode = "201", description = "Foto asociada")
    public ResponseEntity<EventoFotoAdminResponse> agregar(@PathVariable UUID eventoId,
            @Valid @RequestPart("datos") EventoFotoRequest datos, @RequestPart("file") MultipartFile file) {
        try (var stream = file.getInputStream()) {
            var response = service.agregar(eventoId, datos, file.getContentType(), file.getSize(), stream);
            return ResponseEntity.created(URI.create("/api/admin/eventos/" + eventoId + "/fotos/" + response.id())).body(response);
        } catch (IOException ex) {
            throw new StorageException(StorageException.Reason.INVALID_FILE);
        }
    }

    @PutMapping("/{fotoId}")
    @Operation(summary = "Actualizar etiqueta y orden de una foto")
    public EventoFotoAdminResponse actualizar(@PathVariable UUID eventoId, @PathVariable UUID fotoId,
                                               @Valid @RequestBody EventoFotoRequest request) {
        return service.actualizar(eventoId, fotoId, request);
    }

    @PutMapping(value = "/{fotoId}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Reemplazar imagen de una foto",
            description = "Conserva identidad, etiqueta y orden. El archivo anterior se borra despues del commit; el nuevo se compensa ante rollback.")
    public EventoFotoAdminResponse reemplazar(@PathVariable UUID eventoId, @PathVariable UUID fotoId,
                                               @RequestPart("file") MultipartFile file) {
        try (var stream = file.getInputStream()) {
            return service.reemplazar(eventoId, fotoId, file.getContentType(), file.getSize(), stream);
        } catch (IOException ex) {
            throw new StorageException(StorageException.Reason.INVALID_FILE);
        }
    }

    @DeleteMapping("/{fotoId}")
    @Operation(summary = "Eliminar una foto del evento",
            description = "Elimina el registro y solicita borrar su archivo despues del commit. No elimina el evento. Segunda eliminacion devuelve 404.")
    @ApiResponse(responseCode = "204", description = "Foto eliminada")
    public ResponseEntity<Void> eliminar(@PathVariable UUID eventoId, @PathVariable UUID fotoId) {
        service.eliminar(eventoId, fotoId);
        return ResponseEntity.noContent().build();
    }
}
