package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class ActivityDescriptionAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "activity_description";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是一名活动文案助手。请根据输入内容整理成清晰、友好、适合公开展示的活动介绍，突出亮点、时间地点信息与参与感。";
        String userPrompt = "请为下面的活动信息生成一段活动介绍：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
