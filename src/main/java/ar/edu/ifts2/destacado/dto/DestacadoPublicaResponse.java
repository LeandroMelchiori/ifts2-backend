package ar.edu.ifts2.destacado.dto;

import java.net.URI;
import java.util.UUID;

public record DestacadoPublicaResponse(int posicion, UUID noticiaId, UUID eventoId,
        String titulo, String resumen, URI portadaUrl, String enlaceUrl) { }
