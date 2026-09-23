package ar.edu.ifts2.carrera.dto;

import ar.edu.ifts2.carrera.entity.Carrera;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CarreraAdminResponse(UUID id, String nombre, String tituloOtorgado, String descripcion, String duracion, String modalidad, String requisitosIngreso, Integer orden, URI imagenUrl, EstadoPublicacion estado, Instant publicadaAt, Instant createdAt, Instant updatedAt, String imagenObjectKey) {
    public static CarreraAdminResponse from(Carrera item, URI url) {
        return new CarreraAdminResponse(item.getId(), item.getNombre(), item.getTituloOtorgado(), item.getDescripcion(), item.getDuracion(), item.getModalidad(), item.getRequisitosIngreso(), item.getOrden(), url, item.getEstado(), item.getPublicadaAt(), item.getCreatedAt(), item.getUpdatedAt(), item.getImagenObjectKey());
    }
}
