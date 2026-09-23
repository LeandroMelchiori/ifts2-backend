package ar.edu.ifts2.destacado.repository;

import ar.edu.ifts2.destacado.entity.Destacado;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface DestacadoRepository extends JpaRepository<Destacado, Integer> {
    @EntityGraph(attributePaths = {"noticia", "evento"})
    List<Destacado> findAllByOrderByPosicionAsc();

    @Query(value = "select id from carrusel where id = 1 for update", nativeQuery = true)
    Integer bloquearCarrusel();
}
