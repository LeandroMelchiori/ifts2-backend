package ar.edu.ifts2.autoridad.dto;

import ar.edu.ifts2.autoridad.entity.Autoridad;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record AutoridadPublicaResponse(UUID id, String nombre, String apellido, String cargo, String descripcion, Integer orden, URI fotoUrl, Instant publicadaAt) {
    public static AutoridadPublicaResponse from(Autoridad item, URI url) {
        return new AutoridadPublicaResponse(item.getId(), item.getNombre(), item.getApellido(), item.getCargo(), item.getDescripcion(), item.getOrden(), url, item.getPublicadaAt());
    }
}
