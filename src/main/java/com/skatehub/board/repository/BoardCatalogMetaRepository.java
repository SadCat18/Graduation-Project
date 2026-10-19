package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardCatalogMeta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface BoardCatalogMetaRepository extends JpaRepository<BoardCatalogMeta, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from BoardCatalogMeta m where m.id = 1")
    BoardCatalogMeta lockCatalog();
}
