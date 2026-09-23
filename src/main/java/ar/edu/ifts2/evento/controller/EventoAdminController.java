package ar.edu.ifts2.evento.controller;

import ar.edu.ifts2.evento.dto.*;
import ar.edu.ifts2.evento.service.EventoService;
import ar.edu.ifts2.shared.dto.*;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/eventos")
@Tag(name = "Eventos Administration")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Request, parametros o UUID invalidos", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente o invalido", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere ADMIN o EDITOR", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Recurso no encontrado", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Conflicto con los datos o el estado actual", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class EventoAdminController {
    private final EventoService service;
    public EventoAdminController(EventoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar eventos para administracion", description = "Todos los estados salvo filtro opcional; orden createdAt descendente e id ascendente.")
    @ApiResponse(responseCode = "200", description = "Pagina administrativa")
    public PageResponse<EventoAdminResponse> listar(
            @RequestParam(required = false) EstadoPublicacion estado,
            @Parameter(description = "Pagina desde cero, maximo 1000000") @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @Parameter(description = "Cantidad de 1 a 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.listar(estado, page, size); }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar evento en cualquier estado")
    @ApiResponse(responseCode = "200", description = "Recurso encontrado")
    public EventoAdminResponse obtener(@PathVariable UUID id) { return service.obtener(id); }

    @PostMapping
    @Operation(summary = "Crear evento como borrador")
    @ApiResponse(responseCode = "201", description = "Borrador creado; Location identifica el recurso")
    public ResponseEntity<EventoAdminResponse> crear(@Valid @RequestBody EventoRequest request) {
        var response = service.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/eventos/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplazar contenido de evento", description = "Conserva estado y archivos. Los cambios de contenido publicado son visibles inmediatamente. No admite estado, timestamps ni objectKey en el body.")
    @ApiResponse(responseCode = "200", description = "Contenido actualizado")
    public EventoAdminResponse actualizar(@PathVariable UUID id, @Valid @RequestBody EventoRequest request) {
        return service.actualizar(id, request);
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Publicar, retirar o archivar evento", description = "BORRADOR y ARCHIVADA ocultan sin borrar. Repetir estado es idempotente; republicar renueva publicadaAt.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    public EventoAdminResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambiarEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @PutMapping(value = "/{id}/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir o reemplazar portada", description = "JPEG, PNG o WEBP, limite de imagenes (5 MB por defecto). MIME y firma validados. Clave generada bajo eventos/. Reemplazo compensado con PostgreSQL; limpieza fallida requiere revision manual.")
    @ApiResponse(responseCode = "200", description = "Archivo asociado; URL derivada")
    @ApiResponse(responseCode = "413", description = "Archivo demasiado grande", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "415", description = "MIME no permitido", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Fallo del proveedor", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Storage deshabilitado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ar.edu.ifts2.storage.dto.StorageUrlResponse subirArchivo(@PathVariable UUID id,
            @Parameter(description = "Archivo binario", required = true) @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        try (var stream = file.getInputStream()) {
            return service.subirArchivo(id, file.getContentType(), file.getSize(), stream);
        } catch (java.io.IOException ex) {
            throw new ar.edu.ifts2.storage.StorageException(ar.edu.ifts2.storage.StorageException.Reason.INVALID_FILE);
        }
    }

    @DeleteMapping("/{id}/portada")
    @Operation(summary = "Quitar portada", description = "Idempotente si no hay archivo. No elimina el contenido. Limpieza posterior al commit.")
    @ApiResponse(responseCode = "204", description = "Archivo desasociado", content = @Content)
    @ApiResponse(responseCode = "503", description = "Storage deshabilitado", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> quitarArchivo(@PathVariable UUID id) {
        service.quitarArchivo(id);
        return ResponseEntity.noContent().build();
    }
}
