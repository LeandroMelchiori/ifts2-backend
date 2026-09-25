package ar.edu.ifts2.meta.repository;

import ar.edu.ifts2.meta.entity.MetaPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MetaPostRepository extends JpaRepository<MetaPost, String> {
    @Query("select p from MetaPost p where (:visible is null or p.visible = :visible)")
    Page<MetaPost> buscar(@Param("visible") Boolean visible, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MetaPost p where p.id = :id")
    Optional<MetaPost> findByIdForUpdate(@Param("id") String id);
}
