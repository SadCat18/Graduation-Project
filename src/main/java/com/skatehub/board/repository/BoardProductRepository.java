package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardProduct;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardProductRepository extends JpaRepository<BoardProduct, Long> {
    org.springframework.data.domain.Page<BoardProduct> findByActiveTrue(org.springframework.data.domain.Pageable page);
    org.springframework.data.domain.Page<BoardProduct> findByActiveTrueAndCategory(String category, org.springframework.data.domain.Pageable page);
    java.util.Optional<BoardProduct> findByKey(String key);
}
