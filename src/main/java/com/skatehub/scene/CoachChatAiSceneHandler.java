package com.skatehub.scene;

import com.skatehub.pojo.ai.AiRequest;
import org.springframework.stereotype.Component;

@Component
public class CoachChatAiSceneHandler extends AbstractAiSceneHandler {

    @Override
    public String getScene() {
        return "coach_chat";
    }

    @Override
    public AiRequest prepare(AiRequest request) {
        String systemPrompt = "你是滑板社区里的 AI 老师。请用友好、清晰、可执行的方式回答，优先给出练习步骤、安全提醒和循序渐进的建议。"
                + "遇到明显的受伤、疼痛或风险情况时，不做医疗诊断，提醒用户及时线下求助专业人士。";
        String userPrompt = "请作为滑板老师回答下面的问题：\n" + extractInputText(request);
        return buildSceneRequest(request, systemPrompt, userPrompt);
    }
}
