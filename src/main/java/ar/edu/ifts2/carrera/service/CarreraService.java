package ar.edu.ifts2.carrera.service;

import ar.edu.ifts2.carrera.dto.*;
import ar.edu.ifts2.carrera.entity.Carrera;
import ar.edu.ifts2.carrera.repository.CarreraRepository;
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
public class CarreraService {
    private final CarreraRepository repository;
    private final ArchivoStorageService storage;

    public CarreraService(CarreraRepository repository, ArchivoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public PageResponse<CarreraPublicaResponse> listarPublicados(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("orden").and(Sort.by("nombre")).and(Sort.by("id")));
        var items = repository.findByEstado(EstadoPublicacion.PUBLICADA, pageable);
        return PageResponse.from(items.map(this::toPublic));
    }

    public CarreraPublicaResponse obtenerPublicada(UUID id) {
        return toPublic(repository.findByIdAndEstado(id, EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public PageResponse<CarreraAdminResponse> listar(EstadoPublicacion estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var items = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(items.map(this::toAdmin));
    }

    public CarreraAdminResponse obtener(UUID id) { return toAdmin(repository.findById(id).orElseThrow(this::notFound)); }

    @Transactional
    public CarreraAdminResponse crear(CarreraRequest request) {
        return toAdmin(repository.saveAndFlush(new Carrera(request.nombre(), request.tituloOtorgado(), request.descripcion(), request.duracion(), request.modalidad(), request.requisitosIngreso(), request.orden())));
    }

    @Transactional
    public CarreraAdminResponse actualizar(UUID id, CarreraRequest request) {
        Carrera item = findForUpdate(id);
        item.actualizar(request.nombre(), request.tituloOtorgado(), request.descripcion(), request.duracion(), request.modalidad(), request.requisitosIngreso(), request.orden());
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public CarreraAdminResponse cambiarEstado(UUID id, EstadoPublicacion estado) {
        Carrera item = findForUpdate(id);
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    private Carrera findForUpdate(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }

    @Transactional
    public StorageUrlResponse subirArchivo(UUID id, String mime, long size, InputStream content) {
        Carrera item = findForUpdate(id);
        var stored = storage.replaceImage("carreras", item.getImagenObjectKey(), mime, size, content);
        item.cambiarArchivo(stored.objectKey());
        repository.flush();
        return new StorageUrlResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitarArchivo(UUID id) {
        Carrera item = findForUpdate(id);
        storage.removeAfterCommit(item.getImagenObjectKey());
        item.cambiarArchivo(null);
        repository.flush();
    }

    private CarreraAdminResponse toAdmin(Carrera item) { return CarreraAdminResponse.from(item, storage.resolvePublicUrl(item.getImagenObjectKey())); }
    private CarreraPublicaResponse toPublic(Carrera item) { return CarreraPublicaResponse.from(item, storage.resolvePublicUrl(item.getImagenObjectKey())); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Carreras: recurso no encontrado"); }
}
