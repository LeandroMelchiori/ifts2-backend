package ar.edu.ifts2.evento.repository;

import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {
    Page<Evento> findByEstado(EstadoPublicacion estado, Pageable pageable);
    Optional<Evento> findByIdAndEstado(UUID id, EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Evento i where i.id = :id")
    Optional<Evento> findByIdForUpdate(@Param("id") UUID id);

    Page<Evento> findByEstadoAndFechaInicioGreaterThanEqual(EstadoPublicacion estado, Instant desde, Pageable pageable);
}
