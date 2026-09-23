package ar.edu.ifts2.noticia.service;

import ar.edu.ifts2.noticia.dto.NoticiaAdminResponse;
import ar.edu.ifts2.noticia.dto.NoticiaPublicaResponse;
import ar.edu.ifts2.noticia.dto.NoticiaRequest;
import ar.edu.ifts2.noticia.dto.NoticiaResumenResponse;
import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.shared.dto.PageResponse;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NoticiaService {
    private final NoticiaRepository repository;

    public NoticiaService(NoticiaRepository repository) { this.repository = repository; }

    public PageResponse<NoticiaResumenResponse> listarPublicadas(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("publicadaAt").descending().and(Sort.by("id")));
        return PageResponse.from(repository.findByEstado(EstadoNoticia.PUBLICADA, pageable).map(NoticiaResumenResponse::from));
    }

    public NoticiaPublicaResponse obtenerPublicada(UUID id) {
        return NoticiaPublicaResponse.from(repository.findByIdAndEstado(id, EstadoNoticia.PUBLICADA)
                .orElseThrow(this::notFound));
    }

    public PageResponse<NoticiaAdminResponse> listar(EstadoNoticia estado, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id")));
        var noticias = estado == null ? repository.findAll(pageable) : repository.findByEstado(estado, pageable);
        return PageResponse.from(noticias.map(NoticiaAdminResponse::from));
    }

    public NoticiaAdminResponse obtener(UUID id) {
        return NoticiaAdminResponse.from(repository.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public NoticiaAdminResponse crear(NoticiaRequest request) {
        Noticia noticia = new Noticia(request.titulo(), request.resumen(), request.contenido());
        return NoticiaAdminResponse.from(repository.saveAndFlush(noticia));
    }

    @Transactional
    public NoticiaAdminResponse actualizar(UUID id, NoticiaRequest request) {
        Noticia noticia = repository.findByIdForUpdate(id).orElseThrow(this::notFound);
        noticia.actualizar(request.titulo(), request.resumen(), request.contenido());
        repository.flush();
        return NoticiaAdminResponse.from(noticia);
    }

    @Transactional
    public NoticiaAdminResponse cambiarEstado(UUID id, EstadoNoticia estado) {
        Noticia noticia = repository.findByIdForUpdate(id).orElseThrow(this::notFound);
        noticia.cambiarEstado(estado);
        repository.flush();
        return NoticiaAdminResponse.from(noticia);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Noticia no encontrada");
    }
}
