package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardQuote;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardQuoteRepository extends JpaRepository<BoardQuote, Long> {
    java.util.Optional<BoardQuote> findFirstByKeyOrderByVersionDesc(String key);
    java.util.List<BoardQuote> findBySourceProductKeyIn(java.util.Collection<String> keys);
}
