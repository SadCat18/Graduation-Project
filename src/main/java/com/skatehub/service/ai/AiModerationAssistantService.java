package com.skatehub.service.ai;

import com.skatehub.pojo.ai.AiModerationSuggestRequest;
import com.skatehub.pojo.ai.AiModerationSuggestResponse;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.util.AiUtils;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AiModerationAssistantService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("POST", "ACTIVITY", "BULLETIN");
    private static final List<String> ALLOWED_RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    private final AiGatewayService aiGatewayService;

    public AiModerationSuggestResponse suggest(AiModerationSuggestRequest request) {
        validateRequest(request);

        AiResponse response;
        try {
            response = aiGatewayService.chat(buildAiRequest(request));
        } catch (BizException exception) {
            throw new BizException("AI 审核辅助调用失败: " + exception.getMessage());
        }

        if (response == null || !StringUtils.hasText(response.getContent())) {
            throw new BizException("AI 审核辅助未返回有效内容");
        }

        AiModerationSuggestResponse result = parseResponse(response.getContent());
        validateResult(result);
        normalizeResult(result);
        return result;
    }

    private void validateRequest(AiModerationSuggestRequest request) {
        String normalizedType = request.getContentType() == null ? "" : request.getContentType().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw new BizException("不支持的 contentType: " + request.getContentType());
        }
    }

    private AiRequest buildAiRequest(AiModerationSuggestRequest request) {
        return AiRequest.builder()
                .scene("moderation_suggest")
                .systemPrompt(buildSystemPrompt())
                .userPrompt(buildUserPrompt(request))
                .temperature(0.2D)
                .maxTokens(1000)
                .responseFormat("json_object")
                .build();
    }

    private String buildSystemPrompt() {
        return "你是滑板社区的审核辅助助手，只提供风险识别建议，不直接决定通过或驳回。"
                + "请重点识别：广告导流、辱骂攻击、违法违规、虚假信息、联系方式泄露、不适合公开展示的社区内容。"
                + "你必须只返回 JSON，不要输出任何额外说明、前缀、后缀或 Markdown 代码块。"
                + "JSON 必须包含字段：riskLevel、riskPoints、suggestion、normalizedSummary。"
                + "其中 riskLevel 只能是 LOW、MEDIUM、HIGH；riskPoints 必须是字符串数组。"
                + "suggestion 需要给出人工审核建议，normalizedSummary 需要用规范、克制的方式总结内容。";
    }

    private String buildUserPrompt(AiModerationSuggestRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("请对下面的社区内容提供审核辅助建议，并返回 JSON。\n")
                .append("内容类型：").append(request.getContentType().trim().toUpperCase(Locale.ROOT)).append("\n\n")
                .append("标题：\n").append(request.getTitle().trim()).append("\n\n")
                .append("正文：\n").append(request.getContent().trim());
        if (StringUtils.hasText(request.getExtraInfo())) {
            builder.append("\n\n补充信息：\n").append(request.getExtraInfo().trim());
        }
        return builder.toString();
    }

    private AiModerationSuggestResponse parseResponse(String content) {
        return AiUtils.parseJson(content, AiModerationSuggestResponse.class);
    }

    private void validateResult(AiModerationSuggestResponse result) {
        if (result == null) {
            throw new BizException("AI 审核辅助返回结果为空");
        }
        if (!StringUtils.hasText(result.getRiskLevel())
                || !StringUtils.hasText(result.getSuggestion())
                || !StringUtils.hasText(result.getNormalizedSummary())) {
            throw new BizException("AI 审核辅助返回字段不完整");
        }
        String normalizedLevel = result.getRiskLevel().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_RISK_LEVELS.contains(normalizedLevel)) {
            throw new BizException("AI 审核辅助返回了无效风险等级");
        }
    }

    private void normalizeResult(AiModerationSuggestResponse result) {
        result.setRiskLevel(result.getRiskLevel().trim().toUpperCase(Locale.ROOT));
        result.setSuggestion(result.getSuggestion().trim());
        result.setNormalizedSummary(result.getNormalizedSummary().trim());
        result.setRiskPoints(AiUtils.normalizeList(result.getRiskPoints()));
    }

}
