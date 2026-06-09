package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class NewsClassifyAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "news_classify";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是滑板资讯分类助手。请根据内容判断资讯主题，并给出简洁、稳定、适合后台管理使用的分类结果。";
        String userPrompt = "请为下面这段滑板资讯内容做分类：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
