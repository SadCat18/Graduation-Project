package com.skatehub.service.ai;

import com.skatehub.pojo.ai.AiActivityDescriptionRequest;
import com.skatehub.pojo.ai.AiActivityDescriptionResponse;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.util.AiUtils;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiActivityAssistantService {

    private final AiGatewayService aiGatewayService;

    public AiActivityDescriptionResponse generateDescription(AiActivityDescriptionRequest request) {
        AiResponse response;
        try {
            response = aiGatewayService.chat(buildAiRequest(request));
        } catch (BizException exception) {
            throw new BizException("AI 活动发布助手调用失败: " + exception.getMessage());
        }

        if (response == null || !StringUtils.hasText(response.getContent())) {
            throw new BizException("AI 活动发布助手未返回有效内容");
        }

        AiActivityDescriptionResponse result = parseResponse(response.getContent());
        validateResult(result);
        normalizeResult(result);
        return result;
    }

    private AiRequest buildAiRequest(AiActivityDescriptionRequest request) {
        return AiRequest.builder()
                .scene("activity_description")
                .systemPrompt(buildSystemPrompt())
                .userPrompt(buildUserPrompt(request))
                .temperature(0.5D)
                .maxTokens(1400)
                .responseFormat("json_object")
                .build();
    }

    private String buildSystemPrompt() {
        return "你是滑板社区的同城约板活动助手。请根据用户提供的活动信息，"
                + "生成自然、真实、适合社区发布的活动召集文案。"
                + "不要写成商业宣传，也不要夸张营销。"
                + "你必须只返回 JSON，不要输出任何额外说明、前缀、后缀或 Markdown 代码块。"
                + "JSON 必须包含字段：title、description、highlights、tips、suitableFor、riskTips。"
                + "其中 highlights、tips、riskTips 必须返回字符串数组。"
                + "title 要更清晰自然；description 要像真实社区活动召集；"
                + "tips 要偏活动参与提醒；riskTips 要指出天气、装备、安全、迟到、人数控制等潜在风险，没有明显风险时返回空数组。";
    }

    private String buildUserPrompt(AiActivityDescriptionRequest request) {
        return "请根据下面的滑板同城约板活动信息，生成结构化活动发布文案 JSON。\n"
                + "原始标题：\n" + request.getTitle().trim() + "\n\n"
                + "活动时间：\n" + request.getActivityTime().trim() + "\n\n"
                + "活动地点：\n" + buildLocationText(request) + "\n\n"
                + "人数上限：\n" + request.getMaxNum() + " 人\n\n"
                + "补充内容：\n" + request.getContent().trim();
    }

    private String buildLocationText(AiActivityDescriptionRequest request) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(request.getLocation())) {
            parts.add(request.getLocation().trim());
        }
        if (StringUtils.hasText(request.getPlace())) {
            parts.add("场地：" + request.getPlace().trim());
        }
        if (StringUtils.hasText(request.getAddress())) {
            parts.add("地址：" + request.getAddress().trim());
        }
        return String.join("；", parts);
    }

    private AiActivityDescriptionResponse parseResponse(String content) {
        return AiUtils.parseJson(content, AiActivityDescriptionResponse.class);
    }


    private void validateResult(AiActivityDescriptionResponse result) {
        if (result == null) {
            throw new BizException("AI 活动发布助手返回结果为空");
        }
        if (!StringUtils.hasText(result.getTitle())
                || !StringUtils.hasText(result.getDescription())
                || !StringUtils.hasText(result.getSuitableFor())) {
            throw new BizException("AI 活动发布助手返回字段不完整");
        }
    }

    private void normalizeResult(AiActivityDescriptionResponse result) {
        result.setTitle(result.getTitle().trim());
        result.setDescription(result.getDescription().trim());
        result.setSuitableFor(result.getSuitableFor().trim());
        result.setHighlights(AiUtils.normalizeList(result.getHighlights()));
        result.setTips(AiUtils.normalizeList(result.getTips()));
        result.setRiskTips(AiUtils.normalizeList(result.getRiskTips()));
    }

}
