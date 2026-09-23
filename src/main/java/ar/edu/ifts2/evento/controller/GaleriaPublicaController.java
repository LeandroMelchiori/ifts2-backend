package ar.edu.ifts2.evento.controller;

import ar.edu.ifts2.evento.dto.EventoFotoPublicaResponse;
import ar.edu.ifts2.evento.service.EventoFotoService;
import ar.edu.ifts2.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@Tag(name = "Galeria")
public class GaleriaPublicaController {
    private final EventoFotoService service;
    public GaleriaPublicaController(EventoFotoService service) { this.service = service; }

    @GetMapping("/api/galeria")
    @Operation(summary = "Consultar galeria de eventos publicados",
            description = "Filtro opcional por eventoId. Orden: fechaInicio descendente, eventoId, orden e id ascendentes. No incluye portadas ni fotos de eventos retirados. Las URLs son null sin storage.")
    public PageResponse<EventoFotoPublicaResponse> galeria(@RequestParam(required = false) UUID eventoId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.galeriaPublica(eventoId, page, size);
    }

    @GetMapping("/api/eventos/{eventoId}/fotos")
    @Operation(summary = "Consultar fotos de un evento publicado", description = "Borradores y archivados devuelven 404 incluso con JWT. Orden: orden e id ascendentes.")
    public List<EventoFotoPublicaResponse> fotos(@PathVariable UUID eventoId) {
        return service.listarPublicas(eventoId);
    }
}
