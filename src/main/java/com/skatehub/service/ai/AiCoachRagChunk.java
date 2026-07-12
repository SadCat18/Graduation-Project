package com.skatehub.service.ai;

import java.util.List;

public record AiCoachRagChunk(
        String sourceType,
        Long sourceId,
        String title,
        String content,
        String routePath,
        int score,
        List<String> matchSignals
) {
    public AiCoachRagChunk(String sourceType, Long sourceId, String title, String content,
                           String routePath, int score) {
        this(sourceType, sourceId, title, content, routePath, score, List.of());
    }
}
