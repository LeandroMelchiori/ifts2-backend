package ar.edu.ifts2.evento.repository;

import ar.edu.ifts2.evento.entity.EventoFoto;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface EventoFotoRepository extends JpaRepository<EventoFoto, UUID> {
    long countByEventoId(UUID eventoId);
    Optional<EventoFoto> findByIdAndEventoId(UUID id, UUID eventoId);

    @EntityGraph(attributePaths = "evento")
    List<EventoFoto> findByEventoIdOrderByOrdenAscIdAsc(UUID eventoId);

    @EntityGraph(attributePaths = "evento")
    @Query("""
            select f from EventoFoto f where (:estado is null or f.evento.estado = :estado)
            and (:eventoId is null or f.evento.id = :eventoId)
            """)
    Page<EventoFoto> buscar(@Param("estado") EstadoPublicacion estado, @Param("eventoId") UUID eventoId,
                           Pageable pageable);
}
