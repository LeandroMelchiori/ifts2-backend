package ar.edu.ifts2.evento.controller;

import ar.edu.ifts2.evento.dto.EventoFotoAdminResponse;
import ar.edu.ifts2.evento.service.EventoFotoService;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/galeria")
@Tag(name = "Eventos Administration")
@SecurityRequirement(name = "bearerAuth")
public class GaleriaAdminController {
    private final EventoFotoService service;
    public GaleriaAdminController(EventoFotoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar galeria administrativa", description = "Incluye todos los estados. Filtros opcionales por eventoId y estado. Orden por fechaInicio descendente, eventoId, orden e id.")
    public PageResponse<EventoFotoAdminResponse> galeria(@RequestParam(required = false) UUID eventoId,
            @RequestParam(required = false) EstadoPublicacion estado,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.galeriaAdmin(eventoId, estado, page, size);
    }
}
