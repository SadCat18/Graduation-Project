package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class NewsTranslateAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "news_translate";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是滑板资讯翻译助手。请保持原文含义准确，语言自然流畅，适合中文社区资讯阅读。";
        String userPrompt = "请翻译下面这段滑板资讯内容：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
