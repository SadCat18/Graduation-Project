package com.skatehub.service.ai;

import java.util.List;

public record AiCoachRagContext(List<AiCoachRagChunk> chunks, String promptText) {

    public static AiCoachRagContext empty() {
        return new AiCoachRagContext(List.of(), "");
    }
}
