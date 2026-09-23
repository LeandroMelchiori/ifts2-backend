package ar.edu.ifts2.carrera.repository;

import ar.edu.ifts2.carrera.entity.Carrera;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface CarreraRepository extends JpaRepository<Carrera, UUID> {
    Page<Carrera> findByEstado(EstadoPublicacion estado, Pageable pageable);
    Optional<Carrera> findByIdAndEstado(UUID id, EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Carrera i where i.id = :id")
    Optional<Carrera> findByIdForUpdate(@Param("id") UUID id);
}
