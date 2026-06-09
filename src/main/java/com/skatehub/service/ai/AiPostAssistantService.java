package com.skatehub.service.ai;

import com.skatehub.pojo.ai.AiPostPolishRequest;
import com.skatehub.pojo.ai.AiPostPolishResponse;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.util.AiUtils;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPostAssistantService {

    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "技巧交流",
            "装备讨论",
            "路线分享",
            "活动讨论",
            "经验分享"
    );

    private final AiGatewayService aiGatewayService;

    public AiPostPolishResponse polish(AiPostPolishRequest request) {
        AiResponse response;
        try {
            response = aiGatewayService.chat(buildAiRequest(request));
        } catch (BizException exception) {
            throw new BizException("AI 发帖助手调用失败: " + exception.getMessage());
        }

        if (response == null || !StringUtils.hasText(response.getContent())) {
            throw new BizException("AI 发帖助手未返回有效内容");
        }

        AiPostPolishResponse result = parseResponse(response.getContent());
        validateResult(result);
        normalizeResult(result);
        return result;
    }

    private AiRequest buildAiRequest(AiPostPolishRequest request) {
        return AiRequest.builder()
                .scene("post_polish")
                .systemPrompt(buildSystemPrompt())
                .userPrompt(buildUserPrompt(request))
                .temperature(0.4D)
                .maxTokens(1200)
                .responseFormat("json_object")
                .build();
    }

    private String buildSystemPrompt() {
        return "你是滑板社区的发帖助手。请根据用户提供的标题和正文，输出结构化 JSON，"
                + "帮助用户优化社区帖子。你必须只返回 JSON，不要输出任何额外说明、前缀、后缀或 Markdown 代码块。"
                + "JSON 字段必须包含：title、content、category、summary、riskTips。"
                + "其中 category 必须从以下分类中选择一个：技巧交流、装备讨论、路线分享、活动讨论、经验分享。"
                + "title 要更清晰自然，适合社区展示；content 要优化表达、增强可读性；"
                + "summary 要生成一句话摘要；riskTips 要返回字符串数组，若没有明显风险请返回空数组。";
    }

    private String buildUserPrompt(AiPostPolishRequest request) {
        return "请润色下面这篇滑板社区帖子，并按要求返回 JSON。\n"
                + "原始标题：\n" + request.getTitle().trim() + "\n\n"
                + "原始正文：\n" + request.getContent().trim();
    }

    private AiPostPolishResponse parseResponse(String content) {
        return AiUtils.parseJson(content, AiPostPolishResponse.class);
    }

    private void validateResult(AiPostPolishResponse result) {
        if (result == null) {
            throw new BizException("AI 发帖助手返回结果为空");
        }
        if (!StringUtils.hasText(result.getTitle())
                || !StringUtils.hasText(result.getContent())
                || !StringUtils.hasText(result.getCategory())
                || !StringUtils.hasText(result.getSummary())) {
            throw new BizException("AI 发帖助手返回字段不完整");
        }
        if (!ALLOWED_CATEGORIES.contains(result.getCategory().trim())) {
            throw new BizException("AI 发帖助手返回了不支持的分类");
        }
    }

    private void normalizeResult(AiPostPolishResponse result) {
        result.setTitle(result.getTitle().trim());
        result.setContent(result.getContent().trim());
        result.setCategory(result.getCategory().trim());
        result.setSummary(result.getSummary().trim());

        result.setRiskTips(AiUtils.normalizeList(result.getRiskTips()));
    }
}
