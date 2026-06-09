package com.skatehub.dao;

import com.skatehub.pojo.AiCoachSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiCoachSessionRepository extends JpaRepository<AiCoachSession, String> {

    Optional<AiCoachSession> findBySessionIdAndUserId(String sessionId, Long userId);

    List<AiCoachSession> findByUserIdOrderByUpdateTimeDesc(Long userId);
}
