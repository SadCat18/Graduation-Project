package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class NewsTitlePolishAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "news_title_polish";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是滑板资讯标题优化助手。请根据资讯内容优化标题，使标题清晰、准确、适合中文滑板社区展示。";
        String userPrompt = "请优化下面这条滑板资讯的标题：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
