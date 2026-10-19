package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardTemplateRepository extends JpaRepository<BoardTemplate, Long> {
    java.util.Optional<BoardTemplate> findByKey(String key);
}
