package ar.edu.ifts2.enlace.service;

import ar.edu.ifts2.enlace.dto.*;
import ar.edu.ifts2.enlace.entity.Enlace;
import ar.edu.ifts2.enlace.repository.EnlaceRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EnlaceService {
    private final EnlaceRepository repository;

    public EnlaceService(EnlaceRepository repository) {
        this.repository = repository;
    }

    public PageResponse<EnlacePublicaResponse> listarPublicados(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("orden").and(Sort.by("titulo")).and(Sort.by("id")));
        var items = repository.findByEstado(EstadoPublicacion.PUBLICADA, pageable);
        return PageResponse.from(items.map(this::toPublic));
    }

    public EnlacePublicaResponse obtenerPublicada(UUID id) {
        return toPublic(repository.findByIdAndEstado(id, EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public PageResponse<EnlaceAdminResponse> listar(EstadoPublicacion estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var items = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(items.map(this::toAdmin));
    }

    public EnlaceAdminResponse obtener(UUID id) { return toAdmin(repository.findById(id).orElseThrow(this::notFound)); }

    @Transactional
    public EnlaceAdminResponse crear(EnlaceRequest request) {
        return toAdmin(repository.saveAndFlush(new Enlace(request.titulo(), request.descripcion(), request.url(), request.categoria(), request.orden())));
    }

    @Transactional
    public EnlaceAdminResponse actualizar(UUID id, EnlaceRequest request) {
        Enlace item = findForUpdate(id);
        item.actualizar(request.titulo(), request.descripcion(), request.url(), request.categoria(), request.orden());
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public EnlaceAdminResponse cambiarEstado(UUID id, EstadoPublicacion estado) {
        Enlace item = findForUpdate(id);
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    private Enlace findForUpdate(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }

    private EnlaceAdminResponse toAdmin(Enlace item) { return EnlaceAdminResponse.from(item); }
    private EnlacePublicaResponse toPublic(Enlace item) { return EnlacePublicaResponse.from(item); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Enlaces: recurso no encontrado"); }
}
