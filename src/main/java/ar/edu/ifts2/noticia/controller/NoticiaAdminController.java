package ar.edu.ifts2.noticia.controller;

import ar.edu.ifts2.noticia.dto.CambiarEstadoNoticiaRequest;
import ar.edu.ifts2.noticia.dto.NoticiaAdminResponse;
import ar.edu.ifts2.noticia.dto.NoticiaRequest;
import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.AreaContenido;
import ar.edu.ifts2.noticia.service.NoticiaService;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/noticias")
@Tag(name = "News Administration", description = "Gestion de noticias por ADMIN y EDITOR")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Request, UUID, estado o paginacion invalidos",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "JWT ausente, invalido o cuenta sin acceso",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Requiere rol ADMIN o EDITOR",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Error interno",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class NoticiaAdminController {
    private final NoticiaService service;

    public NoticiaAdminController(NoticiaService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar noticias para administracion", description = "Incluye todos los estados salvo filtro opcional. Orden: createdAt descendente, id ascendente.")
    @ApiResponse(responseCode = "200", description = "Pagina de noticias")
    public PageResponse<NoticiaAdminResponse> listar(
            @Parameter(description = "Estado opcional") @RequestParam(required = false) EstadoNoticia estado,
            @RequestParam(required = false) AreaContenido area,
            @RequestParam(required = false) Boolean mostrarEnNovedades,
            @Parameter(description = "Pagina desde cero") @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @Parameter(description = "Cantidad por pagina, entre 1 y 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listar(estado, page, size, area, mostrarEnNovedades);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una noticia en cualquier estado")
    @ApiResponse(responseCode = "200", description = "Noticia encontrada")
    @ApiResponse(responseCode = "404", description = "Noticia inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public NoticiaAdminResponse obtener(@PathVariable UUID id) { return service.obtener(id); }

    @PostMapping
    @Operation(summary = "Crear un borrador", description = "Titulo hasta 200 caracteres, resumen hasta 500 y contenido hasta 50000; todos obligatorios. Siempre crea BORRADOR. Textos planos.")
    @ApiResponse(responseCode = "201", description = "Borrador creado; Location identifica el recurso administrativo")
    public ResponseEntity<NoticiaAdminResponse> crear(@Valid @RequestBody NoticiaRequest request) {
        var response = service.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/noticias/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplazar contenido de una noticia", description = "Requiere titulo, resumen y contenido. Conserva estado y fecha de publicacion. Si esta publicada, los cambios son visibles inmediatamente; para revision previa, pasar primero a BORRADOR.")
    @ApiResponse(responseCode = "200", description = "Contenido actualizado")
    @ApiResponse(responseCode = "404", description = "Noticia inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public NoticiaAdminResponse actualizar(@PathVariable UUID id, @Valid @RequestBody NoticiaRequest request) {
        return service.actualizar(id, request);
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Publicar, retirar o archivar una noticia", description = "PUBLICADA la hace publica y registra la fecha del servidor al entrar en ese estado. BORRADOR y ARCHIVADA la ocultan sin borrar datos. Permite restaurar archivadas. Repetir el estado no altera la fecha; republicar asigna una nueva. Sin programacion futura.")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", description = "Noticia inexistente",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public NoticiaAdminResponse cambiarEstado(@PathVariable UUID id,
                                               @Valid @RequestBody CambiarEstadoNoticiaRequest request) {
        return service.cambiarEstado(id, request.estado());
    }
}
