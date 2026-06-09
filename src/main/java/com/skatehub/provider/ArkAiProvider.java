package com.skatehub.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skatehub.config.AiProperties;
import com.skatehub.pojo.ai.AiMessage;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.pojo.ai.AiUsage;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ArkAiProvider implements AiProvider {

    private static final String PROVIDER_NAME = AiProviders.ARK;
    private static final String DEFAULT_CHAT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String DEFAULT_RESPONSES_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/responses";

    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public AiResponse chat(AiRequest request, AiProperties.EndpointConfig config) {
        String model = resolveModel(request, config);
        String apiKey = resolveApiKey(config);
        boolean useResponsesApi = request != null && (request.isResponsesApiEnabled() || request.isWebSearchEnabled());
        String baseUrl = resolveBaseUrl(config, useResponsesApi);
        Map<String, Object> payload = useResponsesApi
                ? buildResponsesPayload(model, request)
                : buildChatPayload(model, request);

        RestClient restClient = buildRestClient(apiKey, baseUrl);
        try {
            String rawJson = restClient.post()
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return useResponsesApi ? buildResponsesResult(model, rawJson) : buildChatResult(model, rawJson);
        } catch (RestClientException exception) {
            throw new BizException("ark 请求失败: " + exception.getMessage());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("ark 调用失败: " + exception.getMessage());
        }
    }

    private RestClient buildRestClient(String apiKey, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }

    private String resolveModel(AiRequest request, AiProperties.EndpointConfig config) {
        String model = request != null && StringUtils.hasText(request.getModel()) ? request.getModel() : config.getModel();
        if (!StringUtils.hasText(model)) {
            throw new BizException("未配置 AI_MODEL，无法确定 ark 模型");
        }
        return model.trim();
    }

    private String resolveApiKey(AiProperties.EndpointConfig config) {
        String apiKey = config == null ? null : config.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv("AI_API_KEY");
        }
        if (!StringUtils.hasText(apiKey)) {
            apiKey = System.getenv("ARK_API_KEY");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException("未配置 AI_API_KEY 或 ARK_API_KEY，无法调用 ark");
        }
        return apiKey.trim();
    }

    private String resolveBaseUrl(AiProperties.EndpointConfig config, boolean useResponsesApi) {
        String configured = config != null && StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl().trim() : "";
        if (!StringUtils.hasText(configured)) {
            return useResponsesApi ? DEFAULT_RESPONSES_BASE_URL : DEFAULT_CHAT_BASE_URL;
        }
        if (useResponsesApi) {
            if (configured.endsWith("/responses")) {
                return configured;
            }
            if (configured.endsWith("/chat/completions")) {
                return configured.substring(0, configured.length() - "/chat/completions".length()) + "/responses";
            }
            if (configured.endsWith("/api/v3")) {
                return configured + "/responses";
            }
        }
        return configured;
    }

    private Map<String, Object> buildChatPayload(String model, AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", buildChatMessages(request));
        payload.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) {
            payload.put("max_tokens", request.getMaxTokens());
        }
        String responseFormat = normalizeResponseFormat(request.getResponseFormat());
        if (shouldSendResponseFormat(responseFormat)) {
            payload.put("response_format", Map.of("type", responseFormat));
        }
        return payload;
    }

    private Map<String, Object> buildResponsesPayload(String model, AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", buildResponsesInput(request));
        if (request.getMaxTokens() != null) {
            payload.put("max_output_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request != null && request.isWebSearchEnabled()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            Map<String, Object> webSearchTool = new LinkedHashMap<>();
            webSearchTool.put("type", "web_search");
            webSearchTool.put("max_keyword", 3);
            tools.add(webSearchTool);
            payload.put("tools", tools);
        }
        return payload;
    }

    private List<Map<String, Object>> buildChatMessages(AiRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AiMessage message : request.resolveMessages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", normalizeRole(message.getRole()));
            item.put("content", message.getContent());
            messages.add(item);
        }
        return messages;
    }

    private List<Map<String, Object>> buildResponsesInput(AiRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiMessage message : request.resolveMessages()) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("type", "message");
            inputItem.put("role", normalizeRole(message.getRole()));

            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> textItem = new LinkedHashMap<>();
            textItem.put("type", "input_text");
            textItem.put("text", message.getContent().trim());
            content.add(textItem);

            inputItem.put("content", content);
            items.add(inputItem);
        }
        return items;
    }

    private String normalizeResponseFormat(String responseFormat) {
        if (!StringUtils.hasText(responseFormat)) {
            return null;
        }
        return responseFormat.trim().toLowerCase(Locale.ROOT);
    }

    private boolean shouldSendResponseFormat(String responseFormat) {
        return StringUtils.hasText(responseFormat) && !"json_object".equals(responseFormat);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "user";
        }
        String normalized = role.toLowerCase(Locale.ROOT);
        if ("system".equals(normalized) || "assistant".equals(normalized) || "user".equals(normalized)) {
            return normalized;
        }
        return "user";
    }

    private AiResponse buildChatResult(String model, String rawJson) {
        JsonNode root = parseRawJson(rawJson);
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode() || choice.isNull()) {
            throw new BizException("ark 返回结构异常: 缺少 choices");
        }
        JsonNode messageNode = choice.path("message");
        String content = textAt(messageNode, "content");
        if (!StringUtils.hasText(content)) {
            throw new BizException("ark 返回结构异常: 缺少 message.content");
        }
        List<AiMessage> messages = List.of(new AiMessage(firstNonBlank(textAt(messageNode, "role"), "assistant"), content));
        return AiResponse.builder()
                .provider(PROVIDER_NAME)
                .model(firstNonBlank(textAt(root, "model"), model))
                .content(content)
                .rawJson(rawJson)
                .finishReason(firstNonBlank(textAt(choice, "finish_reason"), "stop"))
                .usage(extractUsage(root))
                .messages(messages)
                .build();
    }

    private AiResponse buildResponsesResult(String model, String rawJson) {
        JsonNode root = parseRawJson(rawJson);
        String content = firstNonBlank(
                textAt(root, "output_text"),
                extractResponsesMessageText(root)
        );
        if (!StringUtils.hasText(content)) {
            throw new BizException("ark responses 返回结构异常: 未提取到输出文本");
        }
        List<AiMessage> messages = List.of(new AiMessage("assistant", content));
        return AiResponse.builder()
                .provider(PROVIDER_NAME)
                .model(firstNonBlank(textAt(root, "model"), model))
                .content(content)
                .rawJson(rawJson)
                .finishReason(firstNonBlank(textAt(root, "status"), "completed"))
                .usage(extractUsage(root))
                .messages(messages)
                .build();
    }

    private String extractResponsesMessageText(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(textAt(item, "type"))) {
                continue;
            }
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                String text = firstNonBlank(
                        textAt(contentItem, "text"),
                        textAt(contentItem, "output_text.text")
                );
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(text.trim());
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private JsonNode parseRawJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            throw new BizException("ark 返回为空");
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new BizException("ark 返回结构异常: 无法解析响应 JSON");
        }
    }

    private AiUsage extractUsage(JsonNode root) {
        JsonNode usageNode = root.path("usage");
        if (usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }
        Integer promptTokens = integerAt(usageNode, "prompt_tokens");
        Integer completionTokens = integerAt(usageNode, "completion_tokens");
        Integer totalTokens = integerAt(usageNode, "total_tokens");
        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return null;
        }
        return new AiUsage(promptTokens, completionTokens, totalTokens);
    }

    private String textAt(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            if (current.isArray()) {
                int index;
                try {
                    index = Integer.parseInt(part);
                } catch (NumberFormatException exception) {
                    return null;
                }
                current = current.path(index);
            } else {
                current = current.path(part);
            }
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private Integer integerAt(JsonNode node, String path) {
        String value = textAt(node, path);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
