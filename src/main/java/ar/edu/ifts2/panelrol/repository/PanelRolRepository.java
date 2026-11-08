package ar.edu.ifts2.panelrol.repository;

import ar.edu.ifts2.panelrol.entity.PanelRol;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PanelRolRepository extends JpaRepository<PanelRol, UUID> {
    @EntityGraph(attributePaths = {"usuario", "permissions"})
    Optional<PanelRol> findByUsuarioId(UUID usuarioId);
    boolean existsByUsuarioId(UUID usuarioId);
    boolean existsByUsuarioIdAndIdNot(UUID usuarioId, UUID id);

    @Query("select distinct r from PanelRol r join fetch r.usuario left join fetch r.permissions order by r.name, r.id")
    List<PanelRol> findAllForAdmin();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PanelRol r where r.id = :id")
    Optional<PanelRol> findByIdForUpdate(@Param("id") UUID id);
}
