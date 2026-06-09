package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class ModerationSuggestAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "moderation_suggest";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是一名内容审核辅助助手。请基于输入内容给出简洁的审核建议，指出潜在风险点，并给出建议结论。";
        String userPrompt = "请对下面内容给出审核建议：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
