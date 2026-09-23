package ar.edu.ifts2.enlace.repository;

import ar.edu.ifts2.enlace.entity.Enlace;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface EnlaceRepository extends JpaRepository<Enlace, UUID> {
    Page<Enlace> findByEstado(EstadoPublicacion estado, Pageable pageable);
    Optional<Enlace> findByIdAndEstado(UUID id, EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Enlace i where i.id = :id")
    Optional<Enlace> findByIdForUpdate(@Param("id") UUID id);
}
