package ar.edu.ifts2.autoridad.service;

import ar.edu.ifts2.autoridad.dto.*;
import ar.edu.ifts2.autoridad.entity.Autoridad;
import ar.edu.ifts2.autoridad.repository.AutoridadRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.storage.ArchivoStorageService;
import ar.edu.ifts2.storage.dto.StorageUrlResponse;
import java.io.InputStream;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AutoridadService {
    private final AutoridadRepository repository;
    private final ArchivoStorageService storage;

    public AutoridadService(AutoridadRepository repository, ArchivoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public PageResponse<AutoridadPublicaResponse> listarPublicados(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("orden").and(Sort.by("apellido")).and(Sort.by("id")));
        var items = repository.findByEstado(EstadoPublicacion.PUBLICADA, pageable);
        return PageResponse.from(items.map(this::toPublic));
    }

    public AutoridadPublicaResponse obtenerPublicada(UUID id) {
        return toPublic(repository.findByIdAndEstado(id, EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public PageResponse<AutoridadAdminResponse> listar(EstadoPublicacion estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var items = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(items.map(this::toAdmin));
    }

    public AutoridadAdminResponse obtener(UUID id) { return toAdmin(repository.findById(id).orElseThrow(this::notFound)); }

    @Transactional
    public AutoridadAdminResponse crear(AutoridadRequest request) {
        return toAdmin(repository.saveAndFlush(new Autoridad(request.nombre(), request.apellido(), request.cargo(), request.descripcion(), request.orden())));
    }

    @Transactional
    public AutoridadAdminResponse actualizar(UUID id, AutoridadRequest request) {
        Autoridad item = findForUpdate(id);
        item.actualizar(request.nombre(), request.apellido(), request.cargo(), request.descripcion(), request.orden());
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public AutoridadAdminResponse cambiarEstado(UUID id, EstadoPublicacion estado) {
        Autoridad item = findForUpdate(id);
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    private Autoridad findForUpdate(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }

    @Transactional
    public StorageUrlResponse subirArchivo(UUID id, String mime, long size, InputStream content) {
        Autoridad item = findForUpdate(id);
        var stored = storage.replaceImage("autoridades", item.getFotoObjectKey(), mime, size, content);
        item.cambiarArchivo(stored.objectKey());
        repository.flush();
        return new StorageUrlResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitarArchivo(UUID id) {
        Autoridad item = findForUpdate(id);
        storage.removeAfterCommit(item.getFotoObjectKey());
        item.cambiarArchivo(null);
        repository.flush();
    }

    private AutoridadAdminResponse toAdmin(Autoridad item) { return AutoridadAdminResponse.from(item, storage.resolvePublicUrl(item.getFotoObjectKey())); }
    private AutoridadPublicaResponse toPublic(Autoridad item) { return AutoridadPublicaResponse.from(item, storage.resolvePublicUrl(item.getFotoObjectKey())); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Autoridades: recurso no encontrado"); }
}
