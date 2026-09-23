package ar.edu.ifts2.autoridad.repository;

import ar.edu.ifts2.autoridad.entity.Autoridad;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AutoridadRepository extends JpaRepository<Autoridad, UUID> {
    Page<Autoridad> findByEstado(EstadoPublicacion estado, Pageable pageable);
    Optional<Autoridad> findByIdAndEstado(UUID id, EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Autoridad i where i.id = :id")
    Optional<Autoridad> findByIdForUpdate(@Param("id") UUID id);
}
