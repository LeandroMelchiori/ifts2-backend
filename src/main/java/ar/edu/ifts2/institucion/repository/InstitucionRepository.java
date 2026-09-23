package ar.edu.ifts2.institucion.repository;

import ar.edu.ifts2.institucion.entity.Institucion;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.UUID;

public interface InstitucionRepository extends JpaRepository<Institucion, UUID> {
    Optional<Institucion> findBySingletonTrue();
    Optional<Institucion> findBySingletonTrueAndEstado(EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Institucion i where i.singleton = true")
    Optional<Institucion> findPrincipalForUpdate();
}
