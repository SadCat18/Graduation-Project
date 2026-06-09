package com.skatehub.dao;

import com.skatehub.pojo.AiCoachMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCoachMessageRepository extends JpaRepository<AiCoachMessage, Long> {

    List<AiCoachMessage> findBySessionIdOrderByCreateTimeAscMsgIdAsc(String sessionId);

   List<AiCoachMessage> findTop12BySessionIdOrderByCreateTimeDescMsgIdDesc(String sessionId);

    void deleteBySessionId(String sessionId);
}
