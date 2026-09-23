package ar.edu.ifts2.evento.service;

import ar.edu.ifts2.evento.dto.*;
import ar.edu.ifts2.evento.entity.*;
import ar.edu.ifts2.evento.repository.*;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.*;
import ar.edu.ifts2.storage.ArchivoStorageService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EventoFotoService {
    private static final int MAX_FOTOS = 50;
    private final EventoRepository eventos;
    private final EventoFotoRepository fotos;
    private final ArchivoStorageService storage;

    public EventoFotoService(EventoRepository eventos, EventoFotoRepository fotos, ArchivoStorageService storage) {
        this.eventos = eventos;
        this.fotos = fotos;
        this.storage = storage;
    }

    public List<EventoFotoAdminResponse> listarAdmin(UUID eventoId) {
        eventos.findById(eventoId).orElseThrow(this::eventoNoEncontrado);
        return fotos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream().map(this::toAdmin).toList();
    }

    public List<EventoFotoPublicaResponse> listarPublicas(UUID eventoId) {
        eventos.findByIdAndEstado(eventoId, EstadoPublicacion.PUBLICADA).orElseThrow(this::eventoNoEncontrado);
        return fotos.findByEventoIdOrderByOrdenAscIdAsc(eventoId).stream()
                .filter(f -> f.getEvento().getEstado() == EstadoPublicacion.PUBLICADA).map(this::toPublic).toList();
    }

    public PageResponse<EventoFotoPublicaResponse> galeriaPublica(UUID eventoId, int page, int size) {
        return PageResponse.from(fotos.buscar(EstadoPublicacion.PUBLICADA, eventoId, pageable(page, size)).map(this::toPublic));
    }

    public PageResponse<EventoFotoAdminResponse> galeriaAdmin(UUID eventoId, EstadoPublicacion estado, int page, int size) {
        return PageResponse.from(fotos.buscar(estado, eventoId, pageable(page, size)).map(this::toAdmin));
    }

    @Transactional
    public EventoFotoAdminResponse agregar(UUID eventoId, EventoFotoRequest request, String mime, long size, InputStream content) {
        Evento evento = bloquearEvento(eventoId);
        if (fotos.countByEventoId(eventoId) >= MAX_FOTOS) {
            throw new BusinessConflictException("Un evento admite hasta 50 fotos");
        }
        var stored = storage.replaceImage("eventos", null, mime, size, content);
        return toAdmin(fotos.saveAndFlush(new EventoFoto(evento, stored.objectKey(), request.etiqueta(), request.orden())));
    }

    @Transactional
    public EventoFotoAdminResponse actualizar(UUID eventoId, UUID fotoId, EventoFotoRequest request) {
        bloquearEvento(eventoId);
        EventoFoto foto = buscarFoto(eventoId, fotoId);
        foto.actualizar(request.etiqueta(), request.orden());
        fotos.flush();
        return toAdmin(foto);
    }

    @Transactional
    public EventoFotoAdminResponse reemplazar(UUID eventoId, UUID fotoId, String mime, long size, InputStream content) {
        bloquearEvento(eventoId);
        EventoFoto foto = buscarFoto(eventoId, fotoId);
        var stored = storage.replaceImage("eventos", foto.getObjectKey(), mime, size, content);
        foto.cambiarArchivo(stored.objectKey());
        fotos.flush();
        return toAdmin(foto);
    }

    @Transactional
    public void eliminar(UUID eventoId, UUID fotoId) {
        bloquearEvento(eventoId);
        EventoFoto foto = buscarFoto(eventoId, fotoId);
        storage.removeAfterCommit(foto.getObjectKey());
        fotos.delete(foto);
        fotos.flush();
    }

    private Evento bloquearEvento(UUID id) {
        // Todas las escrituras de fotos comparten el bloqueo del evento, incluido el limite de cantidad.
        return eventos.findByIdForUpdate(id).orElseThrow(this::eventoNoEncontrado);
    }

    private EventoFoto buscarFoto(UUID eventoId, UUID fotoId) {
        return fotos.findByIdAndEventoId(fotoId, eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto no encontrada en el evento"));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by("evento.fechaInicio").descending()
                .and(Sort.by("evento.id", "orden", "id")));
    }

    private EventoFotoAdminResponse toAdmin(EventoFoto f) {
        return EventoFotoAdminResponse.from(f, storage.resolvePublicUrl(f.getObjectKey()));
    }

    private EventoFotoPublicaResponse toPublic(EventoFoto f) {
        return EventoFotoPublicaResponse.from(f, storage.resolvePublicUrl(f.getObjectKey()));
    }

    private ResourceNotFoundException eventoNoEncontrado() {
        return new ResourceNotFoundException("Evento no encontrado");
    }
}
