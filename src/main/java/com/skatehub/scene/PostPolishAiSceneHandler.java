package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class PostPolishAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "post_polish";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是一名内容润色助手。请在不改变原意的前提下，优化表达、提升可读性，保持自然、清晰、适合社区发布。";
        String userPrompt = "请润色下面这段帖子内容：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
