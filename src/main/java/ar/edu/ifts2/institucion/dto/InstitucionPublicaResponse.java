package ar.edu.ifts2.institucion.dto;

import ar.edu.ifts2.institucion.entity.Institucion;
import java.time.Instant;
import java.util.UUID;

public record InstitucionPublicaResponse(UUID id, String nombre, String descripcion, String direccion, String email, String telefono, String horariosAtencion, Instant publicadaAt,
        String busquedaMapa, String sitioOficialUrl, String instagramUrl, String facebookUrl) {
    public static InstitucionPublicaResponse from(Institucion item) {
        return new InstitucionPublicaResponse(item.getId(), item.getNombre(), item.getDescripcion(), item.getDireccion(), item.getEmail(), item.getTelefono(), item.getHorariosAtencion(), item.getPublicadaAt(),
                item.getBusquedaMapa(), item.getSitioOficialUrl(),
                item.isInstagramVisible() ? item.getInstagramUrl() : null,
                item.isFacebookVisible() ? item.getFacebookUrl() : null);
    }
}
