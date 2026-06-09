package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class NewsSummaryAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "news_summary";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是滑板资讯编辑助手。请基于输入内容提炼核心信息，输出清晰、准确、适合社区资讯展示的摘要。";
        String userPrompt = "请为下面这段滑板资讯生成摘要：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
