package ar.edu.ifts2.destacado.service;

import ar.edu.ifts2.destacado.dto.*;
import ar.edu.ifts2.destacado.entity.Destacado;
import ar.edu.ifts2.destacado.repository.DestacadoRepository;
import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.evento.repository.EventoRepository;
import ar.edu.ifts2.meta.entity.MetaPost;
import ar.edu.ifts2.meta.repository.MetaPostRepository;
import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.noticia.repository.NoticiaRepository;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.shared.error.BusinessConflictException;
import ar.edu.ifts2.shared.error.ResourceNotFoundException;
import ar.edu.ifts2.storage.ArchivoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DestacadoService {
    private final DestacadoRepository repository;
    private final NoticiaRepository noticias;
    private final EventoRepository eventos;
    private final ArchivoStorageService storage;
    private final MetaPostRepository meta;

    public DestacadoService(DestacadoRepository repository, NoticiaRepository noticias,
                            EventoRepository eventos, ArchivoStorageService storage, MetaPostRepository meta) {
        this.repository = repository;
        this.noticias = noticias;
        this.eventos = eventos;
        this.storage = storage;
        this.meta = meta;
    }

    public List<DestacadoAdminResponse> listarAdmin() {
        return repository.findAllByOrderByPosicionAsc().stream().map(this::toAdmin).toList();
    }

    public List<DestacadoPublicaResponse> listarPublicos() {
        return repository.findAllByOrderByPosicionAsc().stream().filter(this::disponible)
                .map(this::toAdmin).map(DestacadoAdminResponse::toPublic).toList();
    }

    @Transactional
    public List<DestacadoAdminResponse> reemplazar(DestacadosRequest request) {
        // El bloqueo existe aunque el carrusel este vacio; dos editores nunca mezclan listas.
        repository.bloquearCarrusel();
        Set<UUID> noticiasVistas = new HashSet<>();
        Set<UUID> eventosVistos = new HashSet<>();
        Set<String> metaVistos = new HashSet<>();
        List<Destacado> seleccion = new ArrayList<>();
        for (var ref : request.items()) {
            Noticia noticia = null;
            Evento evento = null;
            MetaPost metaPost = null;
            if (ref.noticiaId() != null) {
                if (!noticiasVistas.add(ref.noticiaId())) throw repetido();
                noticia = noticias.findByIdForUpdate(ref.noticiaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Noticia no encontrada"));
            } else if (ref.eventoId() != null) {
                if (!eventosVistos.add(ref.eventoId())) throw repetido();
                evento = eventos.findByIdForUpdate(ref.eventoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
            } else {
                if (!metaVistos.add(ref.metaPostId())) throw repetido();
                metaPost = meta.findByIdForUpdate(ref.metaPostId())
                        .orElseThrow(() -> new ResourceNotFoundException("Publicacion Meta no encontrada"));
            }
            Destacado item = new Destacado(seleccion.size() + 1, noticia, evento, metaPost);
            if (!disponible(item)) {
                throw new BusinessConflictException("Solo se pueden seleccionar contenidos publicados");
            }
            seleccion.add(item);
        }
        repository.deleteAllInBatch();
        repository.saveAllAndFlush(seleccion);
        return seleccion.stream().map(this::toAdmin).toList();
    }

    private boolean disponible(Destacado item) {
        if (item.getMetaPost() != null) return item.getMetaPost().isVisible();
        return item.getNoticia() != null ? item.getNoticia().getEstado() == EstadoNoticia.PUBLICADA
                : item.getEvento().getEstado() == EstadoPublicacion.PUBLICADA;
    }

    private DestacadoAdminResponse toAdmin(Destacado item) {
        if (item.getNoticia() != null) {
            Noticia n = item.getNoticia();
            return new DestacadoAdminResponse(item.getPosicion(), n.getId(), null, n.getTitulo(), n.getResumen(),
                    storage.resolvePublicUrl(n.getPortadaObjectKey()), n.getEnlaceUrl(), disponible(item), null, "NOTICIA");
        }
        if (item.getMetaPost() != null) {
            MetaPost post = item.getMetaPost();
            String texto = post.getTexto();
            String titulo = texto.isBlank() ? "Instagram" : texto.substring(0, Math.min(texto.length(), 120));
            return new DestacadoAdminResponse(item.getPosicion(), null, null, titulo, texto,
                    storage.resolvePublicUrl(post.getObjectKey()), post.getLinkOriginal(), disponible(item), post.getId(), "META");
        }
        Evento e = item.getEvento();
        return new DestacadoAdminResponse(item.getPosicion(), null, e.getId(), e.getTitulo(), e.getResumen(),
                storage.resolvePublicUrl(e.getPortadaObjectKey()), null, disponible(item), null, "EVENTO");
    }

    private BusinessConflictException repetido() {
        return new BusinessConflictException("No se puede destacar el mismo contenido mas de una vez");
    }
}
