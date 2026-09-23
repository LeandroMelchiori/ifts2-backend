package ar.edu.ifts2.evento.service;

import ar.edu.ifts2.evento.dto.*;
import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.evento.repository.EventoRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.storage.ArchivoStorageService;
import ar.edu.ifts2.storage.dto.StorageUrlResponse;
import java.io.InputStream;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventoService {
    private final EventoRepository repository;
    private final ArchivoStorageService storage;

    public EventoService(EventoRepository repository, ArchivoStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public PageResponse<EventoPublicaResponse> listarPublicados(int page, int size, Instant desde) {
        var pageable = PageRequest.of(page, size, Sort.by("fechaInicio").and(Sort.by("id")));
        var items = desde == null ? repository.findByEstado(EstadoPublicacion.PUBLICADA, pageable)
                : repository.findByEstadoAndFechaInicioGreaterThanEqual(EstadoPublicacion.PUBLICADA, desde, pageable);
        return PageResponse.from(items.map(this::toPublic));
    }

    public EventoPublicaResponse obtenerPublicada(UUID id) {
        return toPublic(repository.findByIdAndEstado(id, EstadoPublicacion.PUBLICADA).orElseThrow(this::notFound));
    }

    public PageResponse<EventoAdminResponse> listar(EstadoPublicacion estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var items = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(items.map(this::toAdmin));
    }

    public EventoAdminResponse obtener(UUID id) { return toAdmin(repository.findById(id).orElseThrow(this::notFound)); }

    @Transactional
    public EventoAdminResponse crear(EventoRequest request) {
        return toAdmin(repository.saveAndFlush(new Evento(request.titulo(), request.resumen(), request.descripcion(), request.fechaInicio(), request.fechaFin(), request.lugar())));
    }

    @Transactional
    public EventoAdminResponse actualizar(UUID id, EventoRequest request) {
        Evento item = findForUpdate(id);
        item.actualizar(request.titulo(), request.resumen(), request.descripcion(), request.fechaInicio(), request.fechaFin(), request.lugar());
        repository.flush();
        return toAdmin(item);
    }

    @Transactional
    public EventoAdminResponse cambiarEstado(UUID id, EstadoPublicacion estado) {
        Evento item = findForUpdate(id);
        item.cambiarEstado(estado);
        repository.flush();
        return toAdmin(item);
    }

    private Evento findForUpdate(UUID id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }

    @Transactional
    public StorageUrlResponse subirArchivo(UUID id, String mime, long size, InputStream content) {
        Evento item = findForUpdate(id);
        var stored = storage.replaceImage("eventos", item.getPortadaObjectKey(), mime, size, content);
        item.cambiarArchivo(stored.objectKey());
        repository.flush();
        return new StorageUrlResponse(stored.objectKey(), storage.resolvePublicUrl(stored.objectKey()));
    }

    @Transactional
    public void quitarArchivo(UUID id) {
        Evento item = findForUpdate(id);
        storage.removeAfterCommit(item.getPortadaObjectKey());
        item.cambiarArchivo(null);
        repository.flush();
    }

    private EventoAdminResponse toAdmin(Evento item) { return EventoAdminResponse.from(item, storage.resolvePublicUrl(item.getPortadaObjectKey())); }
    private EventoPublicaResponse toPublic(Evento item) { return EventoPublicaResponse.from(item, storage.resolvePublicUrl(item.getPortadaObjectKey())); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Eventos: recurso no encontrado"); }
}
