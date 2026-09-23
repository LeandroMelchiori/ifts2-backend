package ar.edu.ifts2.documento.repository;

import ar.edu.ifts2.documento.entity.Documento;
import ar.edu.ifts2.shared.entity.EstadoPublicacion;
import ar.edu.ifts2.documento.entity.TipoDocumento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
    Page<Documento> findByEstado(EstadoPublicacion estado, Pageable pageable);
    Optional<Documento> findByIdAndEstado(UUID id, EstadoPublicacion estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Documento i where i.id = :id")
    Optional<Documento> findByIdForUpdate(@Param("id") UUID id);

    Page<Documento> findByEstadoAndTipo(EstadoPublicacion estado, TipoDocumento tipo, Pageable pageable);
}
