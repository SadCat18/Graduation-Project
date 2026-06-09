package com.skatehub.scene;

import com.skatehub.pojo.ai.AiMessage;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.util.BizException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractAiSceneHandler implements AiSceneHandler {

    protected AiRequest buildSceneRequest(AiRequest request, String systemPrompt, String userPrompt) {
        return AiRequest.builder()
                .provider(request.getProvider())
                .model(request.getModel())
                .scene(request.getScene())
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .responseFormat(request.getResponseFormat())
                .useResponsesApi(request.getUseResponsesApi())
                .webSearchEnabled(request.getWebSearchEnabled())
                .webSearchMaxResults(request.getWebSearchMaxResults())
                .build();
    }

    protected String extractInputText(AiRequest request) {
        if (StringUtils.hasText(request.getUserPrompt())) {
            return request.getUserPrompt().trim();
        }
        if (!CollectionUtils.isEmpty(request.getMessages())) {
            StringBuilder builder = new StringBuilder();
            for (AiMessage message : request.getMessages()) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                String role = StringUtils.hasText(message.getRole()) ? message.getRole().trim() : "user";
                builder.append(role).append(": ").append(message.getContent().trim());
            }
            if (builder.length() > 0) {
                return builder.toString();
            }
        }
        throw new BizException("当前场景缺少可用于生成 prompt 的输入内容");
    }
}
