package ar.edu.ifts2.meta.controller;

import ar.edu.ifts2.meta.dto.*;
import ar.edu.ifts2.meta.service.MetaPostService;
import ar.edu.ifts2.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/meta")
@Tag(name = "Meta - Administracion")
@SecurityRequirement(name = "bearerAuth")
public class MetaAdminController {
    private final MetaPostService service;
    public MetaAdminController(MetaPostService service) { this.service = service; }

    @PostMapping("/sync")
    @Operation(summary = "Importar publicaciones recientes de Instagram",
            description = "ADMIN o EDITOR. Nuevas ocultas; conserva visibilidad, destacados y archivos. Importacion acotada por META_MAX_POSTS. 503 si Meta esta deshabilitado o su token requiere revision.")
    public MetaSyncResponse sync() { return service.sincronizar(); }

    @GetMapping("/posts")
    @Operation(summary = "Listar publicaciones Meta", description = "Filtro visible opcional. Orden por fecha descendente e id ascendente. Sin copia, imagenUrl es null.")
    public PageResponse<MetaPostAdminResponse> posts(@RequestParam(required = false) Boolean visible,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listarAdmin(visible, page, size);
    }

    @PutMapping("/posts/{id}/visibilidad")
    @Operation(summary = "Publicar u ocultar un post Meta",
            description = "Para publicar sin copia, descarga y valida una imagen de preview. Videos y albumes abren su original en Instagram. Ocultar conserva copia y seleccion en destacados, pero lo retira de respuestas publicas.")
    public MetaPostAdminResponse visibilidad(@PathVariable @Pattern(regexp = "[0-9]{1,40}") String id,
            @Valid @RequestBody MetaVisibilidadRequest request) {
        return service.visibilidad(id, request.visible());
    }

    @DeleteMapping("/posts/{id}/storage")
    @Operation(summary = "Liberar copia de un post Meta",
            description = "409 si visible o seleccionado en destacados, incluso oculto. No borra el post. Idempotente; confirma borrado o ausencia antes de informar exito.")
    public MetaStorageResponse liberar(@PathVariable @Pattern(regexp = "[0-9]{1,40}") String id) {
        return service.liberarStorage(id);
    }
}
