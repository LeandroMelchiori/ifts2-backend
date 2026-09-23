package ar.edu.ifts2.carrera.dto;

import ar.edu.ifts2.carrera.entity.Carrera;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CarreraPublicaResponse(UUID id, String nombre, String tituloOtorgado, String descripcion, String duracion, String modalidad, String requisitosIngreso, Integer orden, URI imagenUrl, Instant publicadaAt) {
    public static CarreraPublicaResponse from(Carrera item, URI url) {
        return new CarreraPublicaResponse(item.getId(), item.getNombre(), item.getTituloOtorgado(), item.getDescripcion(), item.getDuracion(), item.getModalidad(), item.getRequisitosIngreso(), item.getOrden(), url, item.getPublicadaAt());
    }
}
