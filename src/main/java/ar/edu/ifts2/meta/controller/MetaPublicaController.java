package ar.edu.ifts2.meta.controller;

import ar.edu.ifts2.meta.dto.MetaPostPublicaResponse;
import ar.edu.ifts2.meta.service.MetaPostService;
import ar.edu.ifts2.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Instagram")
public class MetaPublicaController {
    private final MetaPostService service;
    public MetaPublicaController(MetaPostService service) { this.service = service; }

    @GetMapping("/api/meta/posts")
    @Operation(summary = "Consultar publicaciones visibles de Instagram",
            description = "Feed para inicio, Vida y galeria social. No consulta Meta ni mezcla las fotos de eventos. Sin objectKey ni URLs temporales de Graph. Orden por fecha descendente e id ascendente.")
    public PageResponse<MetaPostPublicaResponse> posts(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listarPublicos(page, size);
    }
}
