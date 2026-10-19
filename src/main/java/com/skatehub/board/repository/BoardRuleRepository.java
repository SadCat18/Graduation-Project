package com.skatehub.board.repository;
import com.skatehub.board.entity.BoardRule;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardRuleRepository extends JpaRepository<BoardRule, Long> {
    java.util.Optional<BoardRule> findByKey(String key);
}
