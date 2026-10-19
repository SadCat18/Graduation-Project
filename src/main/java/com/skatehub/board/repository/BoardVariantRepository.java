package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardVariant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardVariantRepository extends JpaRepository<BoardVariant, Long> {
    java.util.List<BoardVariant> findByProductKeyIn(java.util.Collection<String> keys);
    java.util.Optional<BoardVariant> findByKey(String key);
}
