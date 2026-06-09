package com.skatehub.pojo.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiRequest {

    private static final double DEFAULT_TEMPERATURE = 0.7D;
    private static final String DEFAULT_RESPONSE_FORMAT = "text";

    private String provider;

    private String model;

    @Size(max = 100, message = "scene 长度不能超过 100")
    private String scene;

    @Size(max = 4000, message = "systemPrompt 长度不能超过 4000")
    private String systemPrompt;

    @Size(max = 20000, message = "userPrompt 长度不能超过 20000")
    private String userPrompt;

    @Valid
    @Size(max = 100, message = "messages 数量不能超过 100")
    private List<AiMessage> messages;

    @DecimalMin(value = "0.0", message = "temperature 不能小于 0")
    @DecimalMax(value = "2.0", message = "temperature 不能大于 2")
    private Double temperature;

    @Min(value = 1, message = "maxTokens 必须大于 0")
    private Integer maxTokens;

    @Size(max = 50, message = "responseFormat 长度不能超过 50")
    private String responseFormat;

    /**
     * Whether to call provider Responses API instead of the legacy chat-completions endpoint.
     */
    private Boolean useResponsesApi;

    /**
     * Whether to enable provider-side web search capability.
     */
    private Boolean webSearchEnabled;

    @Min(value = 1, message = "webSearchMaxResults 必须大于 0")
    private Integer webSearchMaxResults;

    public Double getTemperature() {
        return temperature == null ? DEFAULT_TEMPERATURE : temperature;
    }

    public String getResponseFormat() {
        return StringUtils.hasText(responseFormat) ? responseFormat : DEFAULT_RESPONSE_FORMAT;
    }

    public List<AiMessage> getMessages() {
        return messages == null ? List.of() : messages;
    }

    public List<AiMessage> resolveMessages() {
        List<AiMessage> resolved = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            resolved.add(AiMessage.builder().role("system").content(systemPrompt).build());
        }
        if (!CollectionUtils.isEmpty(messages)) {
            resolved.addAll(messages);
        }
        if (StringUtils.hasText(userPrompt)) {
            resolved.add(AiMessage.builder().role("user").content(userPrompt).build());
        }
        return resolved;
    }

    public boolean isResponsesApiEnabled() {
        return Boolean.TRUE.equals(useResponsesApi);
    }

    public boolean isWebSearchEnabled() {
        return Boolean.TRUE.equals(webSearchEnabled);
    }

    @AssertTrue(message = "至少需要提供 messages，或提供 userPrompt")
    public boolean hasPromptContent() {
        return !CollectionUtils.isEmpty(messages) || StringUtils.hasText(userPrompt);
    }

    @AssertTrue(message = "仅传 systemPrompt 不足以发起 AI 请求，请同时提供 userPrompt 或 messages")
    public boolean hasValidPromptCombination() {
        return !StringUtils.hasText(systemPrompt) || StringUtils.hasText(userPrompt) || !CollectionUtils.isEmpty(messages);
    }
}
