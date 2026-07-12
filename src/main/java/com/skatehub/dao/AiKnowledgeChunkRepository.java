package com.skatehub.dao;

import com.skatehub.pojo.ai.AiKnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiKnowledgeChunkRepository extends JpaRepository<AiKnowledgeChunk, Long> {

    Optional<AiKnowledgeChunk> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<AiKnowledgeChunk> findByPublicVisibleOrderByUpdateTimeDesc(String publicVisible);
}
