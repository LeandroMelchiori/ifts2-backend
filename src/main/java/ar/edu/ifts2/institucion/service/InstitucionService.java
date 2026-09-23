package ar.edu.ifts2.institucion.service;

import ar.edu.ifts2.institucion.dto.*;
import ar.edu.ifts2.institucion.entity.Institucion;
import ar.edu.ifts2.institucion.repository.InstitucionRepository;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstitucionService {
    private final InstitucionRepository repository;

    public InstitucionService(InstitucionRepository repository) {
        this.repository = repository;
    }

    public InstitucionPublicaResponse obtenerPublicada() {
        return toPublic(repository.findBySingletonTrueAndEstado(EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public InstitucionAdminResponse obtener() {
        return toAdmin(repository.findBySingletonTrue().orElseThrow(this::notFound));
    }

    @Transactional
    public InstitucionAdminResponse actualizar(InstitucionRequest request) {
        var existing = repository.findPrincipalForUpdate();
        Institucion item = existing.orElseGet(() -> new Institucion(request.nombre(), request.descripcion(), request.direccion(), request.email(), request.telefono(), request.horariosAtencion()));
        item.actualizar(request.nombre(), request.descripcion(), request.direccion(), request.email(), request.telefono(), request.horariosAtencion());
        return toAdmin(repository.saveAndFlush(item));
    }

    @Transactional
    public InstitucionAdminResponse cambiarEstado(EstadoPublicacion estado) {
        Institucion item = repository.findPrincipalForUpdate().orElseThrow(this::notFound);
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public InstitucionAdminResponse actualizarDatosSitio(DatosSitioRequest request) {
        Institucion item = repository.findPrincipalForUpdate().orElseThrow(this::notFound);
        item.actualizarDatosSitio(request.direccion(), request.email(), request.telefono(), request.busquedaMapa(),
                request.sitioOficialUrl(), request.instagramUrl(), request.instagramVisible(),
                request.facebookUrl(), request.facebookVisible());
        repository.flush();
        return toAdmin(item);
    }

    private InstitucionAdminResponse toAdmin(Institucion item) { return InstitucionAdminResponse.from(item); }
    private InstitucionPublicaResponse toPublic(Institucion item) { return InstitucionPublicaResponse.from(item); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Institucion: recurso no encontrado"); }
}
