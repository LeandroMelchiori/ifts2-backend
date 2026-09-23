package ar.edu.ifts2.noticia.repository;

import ar.edu.ifts2.noticia.entity.EstadoNoticia;
import ar.edu.ifts2.noticia.entity.Noticia;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface NoticiaRepository extends JpaRepository<Noticia, UUID> {
    Page<Noticia> findByEstado(EstadoNoticia estado, Pageable pageable);

    Optional<Noticia> findByIdAndEstado(UUID id, EstadoNoticia estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Noticia n where n.id = :id")
    Optional<Noticia> findByIdForUpdate(@Param("id") UUID id);
}
