package com.skatehub.service;

import com.skatehub.pojo.ai.AiModerationSuggestRequest;
import com.skatehub.pojo.ai.AiModerationSuggestResponse;
import com.skatehub.util.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ModerationRuleService {

    private static final Pattern EXTERNAL_LINK = Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT_INFO = Pattern.compile("\\b(qq|wechat|weixin|vx|telegram|phone)\\b|\\d{6,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROMOTION = Pattern.compile("\\b(discount|private deal|cash|loan|casino|cheap)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_KEYWORD = Pattern.compile("约架|辱骂|骚扰|裸聊|赌博|贷款|代刷|外挂|violence|harassment|casino|loan", Pattern.CASE_INSENSITIVE);

    public AiModerationSuggestResponse evaluate(AiModerationSuggestRequest request) {
        String title = request == null ? "" : safe(request.getTitle());
        String content = request == null ? "" : safe(request.getContent());
        String text = (title + " " + content).toLowerCase(Locale.ROOT);
        List<String> points = new ArrayList<>();
        if (EXTERNAL_LINK.matcher(text).find()) {
            points.add("EXTERNAL_LINK");
        }
        if (CONTACT_INFO.matcher(text).find()) {
            points.add("CONTACT_INFO");
        }
        if (PROMOTION.matcher(text).find()) {
            points.add("PROMOTION");
        }
        if (SENSITIVE_KEYWORD.matcher(text).find()) {
            points.add("SENSITIVE_KEYWORD");
        }
        if (linkCount(text) > 3) {
            points.add("EXCESSIVE_LINKS");
        }
        if (looksRepeated(content)) {
            points.add("REPEATED_TEXT");
        }
        if (visibleLength(content) < minimumContentLength(request == null ? "" : request.getContentType())) {
            points.add("TOO_SHORT");
        }

        AiModerationSuggestResponse response = new AiModerationSuggestResponse();
        response.setRiskPoints(points);
        response.setRiskLevel(points.size() >= 2 ? "HIGH" : points.isEmpty() ? "LOW" : "MEDIUM");
        response.setSuggestion(points.isEmpty()
                ? "No deterministic rule hit. Continue normal review."
                : "Hold for manual review before publishing.");
        response.setNormalizedSummary(title.isBlank()
                ? summarize(content)
                : title + " - " + summarize(content));
        return response;
    }

    public void assertPublishable(String contentType, String title, String content) {
        AiModerationSuggestRequest request = new AiModerationSuggestRequest();
        request.setContentType(contentType == null ? "CONTENT" : contentType);
        request.setTitle(title == null ? contentType == null ? "CONTENT" : contentType : title);
        request.setContent(content == null ? "" : content);
        AiModerationSuggestResponse result = evaluate(request);
        List<String> points = result.getRiskPoints() == null ? List.of() : result.getRiskPoints();
        if (points.contains("TOO_SHORT")) {
            throw new BizException("内容过短，请补充更具体的信息");
        }
        if (points.contains("REPEATED_TEXT")) {
            throw new BizException("内容存在大量重复文本，请整理后再发布");
        }
        if (points.contains("EXCESSIVE_LINKS")) {
            throw new BizException("内容包含过多外部链接，请减少链接数量");
        }
    }

    private int linkCount(String value) {
        java.util.regex.Matcher matcher = EXTERNAL_LINK.matcher(value == null ? "" : value);
        int count = 0;
        while (matcher.find()) {
            count += 1;
        }
        return count;
    }

    private boolean looksRepeated(String value) {
        String normalized = safe(value).replaceAll("\\s+", "");
        if (normalized.length() < 12) {
            return false;
        }
        for (int size = 2; size <= Math.min(12, normalized.length() / 3); size++) {
            String unit = normalized.substring(0, size);
            if (unit.repeat(normalized.length() / size).equals(normalized.substring(0, size * (normalized.length() / size)))) {
                return true;
            }
        }
        return Pattern.compile("(.{2,8})\\1{3,}").matcher(normalized).find();
    }

    private int visibleLength(String value) {
        return safe(value).replaceAll("\\s+", "").length();
    }

    private int minimumContentLength(String contentType) {
        return "COMMENT".equalsIgnoreCase(contentType) ? 2 : 8;
    }

    private String summarize(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
