package ar.edu.ifts2.destacado.controller;

import ar.edu.ifts2.destacado.dto.DestacadoPublicaResponse;
import ar.edu.ifts2.destacado.service.DestacadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/destacados")
@Tag(name = "Destacados")
public class DestacadoPublicaController {
    private final DestacadoService service;
    public DestacadoPublicaController(DestacadoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar carrusel publico",
            description = "Solo seleccionados actualmente publicados, ordenados por posicion. Las posiciones pueden tener huecos al retirar contenido. No expone objectKeys.")
    public List<DestacadoPublicaResponse> listar() { return service.listarPublicos(); }
}
