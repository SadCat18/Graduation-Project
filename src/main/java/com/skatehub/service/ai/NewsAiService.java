package com.skatehub.service.ai;

import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.pojo.ai.NewsAiStepResult;
import com.skatehub.pojo.ai.NewsClassificationAiResult;
import com.skatehub.pojo.ai.NewsSummaryAiResult;
import com.skatehub.pojo.ai.NewsTitlePolishAiResult;
import com.skatehub.pojo.ai.NewsTranslationAiResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.skatehub.util.AiUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsAiService {

    public static final String SCENE_SUMMARY = "news_summary";
    public static final String SCENE_TRANSLATE = "news_translate";
    public static final String SCENE_CLASSIFY = "news_classify";
    public static final String SCENE_TITLE_POLISH = "news_title_polish";

    private static final String FALLBACK_CATEGORY = "未分类";
    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "赛事资讯",
            "装备动态",
            "技巧教学",
            "社区动态",
            "官方公告",
            "品牌资讯"
    );

    private final AiGatewayService aiGatewayService;

    public NewsSummaryAiResult summarizeNews(String title, String content) {
        NewsAiStepResult<NewsSummaryAiResult> step = summarizeNewsStep(title, content);
        if (step.isSuccess() && step.getData() != null) {
            return step.getData();
        }
        return NewsSummaryAiResult.builder()
                .summary("")
                .highlights(List.of())
                .build();
    }

    public NewsTranslationAiResult translateNews(String title, String content) {
        NewsAiStepResult<NewsTranslationAiResult> step = translateNewsStep(title, content);
        if (step.isSuccess() && step.getData() != null) {
            return step.getData();
        }
        return NewsTranslationAiResult.builder()
                .translatedTitle(AiUtils.defaultText(title))
                .translatedContent(AiUtils.defaultText(content))
                .build();
    }

    public NewsClassificationAiResult classifyNews(String title, String content) {
        NewsAiStepResult<NewsClassificationAiResult> step = classifyNewsStep(title, content);
        if (step.isSuccess() && step.getData() != null) {
            return step.getData();
        }
        return NewsClassificationAiResult.builder()
                .category(FALLBACK_CATEGORY)
                .reason("")
                .build();
    }

    public NewsTitlePolishAiResult polishNewsTitle(String title, String content) {
        NewsAiStepResult<NewsTitlePolishAiResult> step = polishNewsTitleStep(title, content);
        if (step.isSuccess() && step.getData() != null) {
            return step.getData();
        }
        return NewsTitlePolishAiResult.builder()
                .title(AiUtils.defaultText(title))
                .subtitle("")
                .build();
    }

    public NewsAiStepResult<NewsSummaryAiResult> summarizeNewsStep(String title, String content) {
        return executeStructuredStep(buildSummaryRequest(title, content), NewsSummaryAiResult.class,
                result -> normalizeSummaryResult(result),
                NewsSummaryAiResult.builder().summary("").highlights(List.of()).build());
    }

    public NewsAiStepResult<NewsTranslationAiResult> translateNewsStep(String title, String content) {
        return executeStructuredStep(buildTranslationRequest(title, content), NewsTranslationAiResult.class,
                result -> normalizeTranslationResult(result, title, content),
                NewsTranslationAiResult.builder()
                        .translatedTitle(AiUtils.defaultText(title))
                        .translatedContent(AiUtils.defaultText(content))
                        .build());
    }

    public NewsAiStepResult<NewsClassificationAiResult> classifyNewsStep(String title, String content) {
        return executeStructuredStep(buildClassificationRequest(title, content), NewsClassificationAiResult.class,
                this::normalizeClassificationResult,
                NewsClassificationAiResult.builder().category(FALLBACK_CATEGORY).reason("").build());
    }

    public NewsAiStepResult<NewsTitlePolishAiResult> polishNewsTitleStep(String title, String content) {
        return executeStructuredStep(buildTitlePolishRequest(title, content), NewsTitlePolishAiResult.class,
                result -> normalizeTitleResult(result, title),
                NewsTitlePolishAiResult.builder().title(AiUtils.defaultText(title)).subtitle("").build());
    }

    public AiRequest buildSummaryRequest(String title, String content) {
        return AiRequest.builder()
                .scene(SCENE_SUMMARY)
                .systemPrompt("""
                        你是滑板资讯编辑助手。
                        请将输入内容整理为适合中文滑板社区阅读的资讯摘要。
                        只返回 JSON，不要输出额外说明或 Markdown 代码块。
                        JSON 必须包含字段：summary、highlights。
                        其中 summary 是 1 到 2 句话的中文摘要，highlights 是 2 到 4 条字符串数组。
                        表达要准确、简洁，适合资讯列表和详情页简介使用。
                        """)
                .userPrompt(buildNewsPrompt(title, content))
                .temperature(0.3D)
                .maxTokens(800)
                .responseFormat("json_object")
                .build();
    }

    public AiRequest buildTranslationRequest(String title, String content) {
        return AiRequest.builder()
                .scene(SCENE_TRANSLATE)
                .systemPrompt("""
                        你是滑板资讯翻译助手。
                        请将输入资讯翻译或整理为自然、流畅、适合中文滑板社区阅读的中文内容。
                        只返回 JSON，不要输出额外说明或 Markdown 代码块。
                        JSON 必须包含字段：translatedTitle、translatedContent。
                        translatedTitle 要适合作为资讯标题，translatedContent 要忠实原意并便于中文用户阅读。
                        """)
                .userPrompt(buildNewsPrompt(title, content))
                .temperature(0.2D)
                .maxTokens(2200)
                .responseFormat("json_object")
                .build();
    }

    public AiRequest buildClassificationRequest(String title, String content) {
        return AiRequest.builder()
                .scene(SCENE_CLASSIFY)
                .systemPrompt("""
                        你是滑板资讯分类助手。
                        请根据资讯内容从以下分类中选择一个最合适的结果：
                        赛事资讯、装备动态、技巧教学、社区动态、官方公告、品牌资讯。
                        只返回 JSON，不要输出额外说明或 Markdown 代码块。
                        JSON 必须包含字段：category、reason。
                        category 必须是给定分类之一，reason 用一句中文解释分类依据。
                        """)
                .userPrompt(buildNewsPrompt(title, content))
                .temperature(0.1D)
                .maxTokens(500)
                .responseFormat("json_object")
                .build();
    }

    public AiRequest buildTitlePolishRequest(String title, String content) {
        return AiRequest.builder()
                .scene(SCENE_TITLE_POLISH)
                .systemPrompt("""
                        你是滑板资讯标题优化助手。
                        请根据资讯内容优化标题，使其更清晰、自然、适合中文滑板社区展示。
                        只返回 JSON，不要输出额外说明或 Markdown 代码块。
                        JSON 必须包含字段：title、subtitle。
                        title 用于最终资讯标题，subtitle 为可选补充说明，没有则返回空字符串。
                        避免夸张营销和标题党表达。
                        """)
                .userPrompt(buildNewsPrompt(title, content))
                .temperature(0.4D)
                .maxTokens(400)
                .responseFormat("json_object")
                .build();
    }

    private String buildNewsPrompt(String title, String content) {
        return "资讯标题：\n" + AiUtils.defaultText(title) + "\n\n"
                + "资讯正文：\n" + AiUtils.defaultText(content);
    }

    private <T> NewsAiStepResult<T> executeStructuredStep(
            AiRequest request,
            Class<T> clazz,
            java.util.function.Function<T, T> normalizer,
            T fallback) {
        var route = aiGatewayService.resolveConfig(request);
        try {
            AiResponse response = aiGatewayService.chat(request);
            T parsed = parseJsonResponse(response, clazz);
            return NewsAiStepResult.<T>builder()
                    .scene(request.getScene())
                    .provider(AiUtils.firstNonBlank(response == null ? null : response.getProvider(), route.getProvider()))
                    .model(AiUtils.firstNonBlank(response == null ? null : response.getModel(), route.getModel()))
                    .success(true)
                    .data(normalizer.apply(parsed))
                    .build();
        } catch (Exception exception) {
            return NewsAiStepResult.<T>builder()
                    .scene(request.getScene())
                    .provider(route.getProvider())
                    .model(route.getModel())
                    .success(false)
                    .errorMessage(exception.getMessage())
                    .data(fallback)
                    .build();
        }
    }

    private <T> T parseJsonResponse(AiResponse response, Class<T> clazz) throws JsonProcessingException {
        return AiUtils.parseJsonQuietly(response == null ? "" : response.getContent(), clazz);
    }

    private NewsSummaryAiResult normalizeSummaryResult(NewsSummaryAiResult result) {
        if (result == null) {
            return NewsSummaryAiResult.builder().summary("").highlights(List.of()).build();
        }
        return NewsSummaryAiResult.builder()
                .summary(AiUtils.defaultText(result.getSummary()))
                .highlights(AiUtils.normalizeList(result.getHighlights()))
                .build();
    }

    private NewsTranslationAiResult normalizeTranslationResult(NewsTranslationAiResult result, String title, String content) {
        if (result == null) {
            return NewsTranslationAiResult.builder()
                    .translatedTitle(AiUtils.defaultText(title))
                    .translatedContent(AiUtils.defaultText(content))
                    .build();
        }
        return NewsTranslationAiResult.builder()
                .translatedTitle(AiUtils.firstNonBlank(result.getTranslatedTitle(), title))
                .translatedContent(AiUtils.firstNonBlank(result.getTranslatedContent(), content))
                .build();
    }

    private NewsClassificationAiResult normalizeClassificationResult(NewsClassificationAiResult result) {
        if (result == null || !StringUtils.hasText(result.getCategory())) {
            return NewsClassificationAiResult.builder()
                    .category(FALLBACK_CATEGORY)
                    .reason("")
                    .build();
        }
        String category = result.getCategory().trim();
        if (!ALLOWED_CATEGORIES.contains(category)) {
            category = FALLBACK_CATEGORY;
        }
        return NewsClassificationAiResult.builder()
                .category(category)
                .reason(AiUtils.defaultText(result.getReason()))
                .build();
    }

    private NewsTitlePolishAiResult normalizeTitleResult(NewsTitlePolishAiResult result, String title) {
        if (result == null) {
            return NewsTitlePolishAiResult.builder()
                    .title(AiUtils.defaultText(title))
                    .subtitle("")
                    .build();
        }
        return NewsTitlePolishAiResult.builder()
                .title(AiUtils.firstNonBlank(result.getTitle(), title))
                .subtitle(AiUtils.defaultText(result.getSubtitle()))
                .build();
    }

}
