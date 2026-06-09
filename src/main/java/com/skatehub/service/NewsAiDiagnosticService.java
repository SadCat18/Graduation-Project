package com.skatehub.service;

import com.skatehub.config.AiProperties;
import com.skatehub.pojo.admin.NewsAiPreviewRequest;
import com.skatehub.pojo.admin.NewsAiPreviewResponse;
import com.skatehub.pojo.admin.NewsAiRouteInfo;
import com.skatehub.pojo.admin.NewsConfigCheckResponse;
import com.skatehub.pojo.ai.NewsAiStepResult;
import com.skatehub.pojo.ai.NewsClassificationAiResult;
import com.skatehub.pojo.ai.NewsSummaryAiResult;
import com.skatehub.pojo.ai.NewsTitlePolishAiResult;
import com.skatehub.pojo.ai.NewsTranslationAiResult;
import com.skatehub.service.ai.NewsAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsAiDiagnosticService {

    private final AiProperties aiProperties;
    private final NewsAiService newsAiService;

    public NewsConfigCheckResponse checkNewsConfig() {
        AiProperties.EndpointConfig config = aiProperties.resolveSceneConfig(NewsAiService.SCENE_SUMMARY);
        List<String> missingItems = new ArrayList<>();
        if (!StringUtils.hasText(config.getProvider())) {
            missingItems.add("AI_PROVIDER_NEWS");
        }
        if (!StringUtils.hasText(config.getModel())) {
            missingItems.add("AI_MODEL_NEWS");
        }
        if (!StringUtils.hasText(config.getBaseUrl())) {
            missingItems.add("AI_BASE_URL_NEWS");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            missingItems.add("AI_API_KEY_NEWS");
        }
        boolean enabled = StringUtils.hasText(config.getProvider())
                && StringUtils.hasText(config.getModel())
                && StringUtils.hasText(config.getApiKey());
        return NewsConfigCheckResponse.builder()
                .enabled(enabled)
                .provider(defaultText(config.getProvider()))
                .model(defaultText(config.getModel()))
                .baseUrlConfigured(StringUtils.hasText(config.getBaseUrl()))
                .apiKeyConfigured(StringUtils.hasText(config.getApiKey()))
                .dedicatedNewsConfig(hasDedicatedNewsConfig())
                .missingItems(missingItems)
                .build();
    }

    public NewsAiPreviewResponse preview(NewsAiPreviewRequest request) {
        String originTitle = defaultText(request == null ? null : request.getOriginTitle());
        String originContent = defaultText(request == null ? null : request.getOriginContent());
        String language = defaultText(request == null ? null : request.getLanguage()).toLowerCase(Locale.ROOT);

        NewsAiStepResult<NewsTranslationAiResult> translationStep = shouldTranslate(language, originTitle, originContent)
                ? newsAiService.translateNewsStep(originTitle, originContent)
                : NewsAiStepResult.<NewsTranslationAiResult>builder()
                .scene(NewsAiService.SCENE_TRANSLATE)
                .provider(routeForScene(NewsAiService.SCENE_TRANSLATE).getProvider())
                .model(routeForScene(NewsAiService.SCENE_TRANSLATE).getModel())
                .success(true)
                .data(NewsTranslationAiResult.builder()
                        .translatedTitle(originTitle)
                        .translatedContent(originContent)
                        .build())
                .build();

        String previewTitle = firstNonBlank(
                translationStep.getData() == null ? null : translationStep.getData().getTranslatedTitle(),
                originTitle
        );
        String previewContent = firstNonBlank(
                translationStep.getData() == null ? null : translationStep.getData().getTranslatedContent(),
                originContent
        );

        NewsAiStepResult<NewsSummaryAiResult> summaryStep = newsAiService.summarizeNewsStep(previewTitle, previewContent);
        NewsAiStepResult<NewsClassificationAiResult> classificationStep = newsAiService.classifyNewsStep(previewTitle, previewContent);
        NewsAiStepResult<NewsTitlePolishAiResult> titleStep = newsAiService.polishNewsTitleStep(previewTitle, previewContent);

        List<NewsAiRouteInfo> routes = List.of(
                toRouteInfo(translationStep),
                toRouteInfo(summaryStep),
                toRouteInfo(classificationStep),
                toRouteInfo(titleStep)
        );

        boolean success = routes.stream().allMatch(NewsAiRouteInfo::isSuccess);
        boolean partial = routes.stream().anyMatch(NewsAiRouteInfo::isSuccess) && !success;

        String providerUsed = firstNonBlank(
                summaryStep.getProvider(),
                titleStep.getProvider(),
                classificationStep.getProvider(),
                translationStep.getProvider()
        );
        String modelUsed = firstNonBlank(
                summaryStep.getModel(),
                titleStep.getModel(),
                classificationStep.getModel(),
                translationStep.getModel()
        );

        return NewsAiPreviewResponse.builder()
                .aiTitle(firstNonBlank(titleStep.getData() == null ? null : titleStep.getData().getTitle(), previewTitle))
                .aiSummary(defaultText(summaryStep.getData() == null ? null : summaryStep.getData().getSummary()))
                .aiCategory(firstNonBlank(classificationStep.getData() == null ? null : classificationStep.getData().getCategory(), "未分类"))
                .aiTranslatedContent(defaultText(translationStep.getData() == null ? null : translationStep.getData().getTranslatedContent()))
                .riskLevel(success ? "LOW" : (partial ? "MEDIUM" : "HIGH"))
                .providerUsed(providerUsed)
                .modelUsed(modelUsed)
                .scene(NewsAiService.SCENE_SUMMARY)
                .success(success)
                .message(success ? "资讯 AI 预览完成" : buildPreviewMessage(routes))
                .routes(routes)
                .build();
    }

    private NewsAiRouteInfo toRouteInfo(NewsAiStepResult<?> step) {
        AiProperties.EndpointConfig route = routeForScene(step.getScene());
        boolean sceneOverride = hasSceneOverride(step.getScene());
        boolean dedicatedNewsConfig = hasDedicatedNewsConfig();
        boolean newsScene = isNewsScene(step.getScene());
        return NewsAiRouteInfo.builder()
                .scene(step.getScene())
                .provider(firstNonBlank(step.getProvider(), route.getProvider()))
                .model(firstNonBlank(step.getModel(), route.getModel()))
                .baseUrlConfigured(StringUtils.hasText(route.getBaseUrl()))
                .apiKeyConfigured(StringUtils.hasText(route.getApiKey()))
                .newsScene(newsScene)
                .dedicatedNewsConfig(dedicatedNewsConfig)
                .sceneOverrideConfigured(sceneOverride)
                .fallbackToDefault(newsScene && !dedicatedNewsConfig && !sceneOverride)
                .success(step.isSuccess())
                .errorMessage(defaultText(step.getErrorMessage()))
                .build();
    }

    private AiProperties.EndpointConfig routeForScene(String scene) {
        return aiProperties.resolveSceneConfig(scene);
    }

    private boolean hasDedicatedNewsConfig() {
        AiProperties.EndpointConfig news = aiProperties.getNews();
        return news != null
                && (StringUtils.hasText(news.getProvider())
                || StringUtils.hasText(news.getModel())
                || StringUtils.hasText(news.getBaseUrl())
                || StringUtils.hasText(news.getApiKey()));
    }

    private boolean hasSceneOverride(String scene) {
        if (!StringUtils.hasText(scene)) {
            return false;
        }
        Map<String, AiProperties.EndpointConfig> scenes = aiProperties.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            return false;
        }
        return scenes.keySet().stream()
                .filter(StringUtils::hasText)
                .anyMatch(key -> key.trim().equalsIgnoreCase(scene.trim()));
    }

    private boolean isNewsScene(String scene) {
        return List.of(
                NewsAiService.SCENE_SUMMARY,
                NewsAiService.SCENE_TRANSLATE,
                NewsAiService.SCENE_CLASSIFY,
                NewsAiService.SCENE_TITLE_POLISH
        ).stream().anyMatch(item -> item.equalsIgnoreCase(defaultText(scene)));
    }

    private boolean shouldTranslate(String language, String title, String content) {
        if ("en".equals(language) || "english".equals(language)) {
            return true;
        }
        String combined = (defaultText(title) + "\n" + defaultText(content)).trim();
        return !combined.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF)
                && combined.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
    }

    private String buildPreviewMessage(List<NewsAiRouteInfo> routes) {
        return routes.stream()
                .filter(item -> !item.isSuccess())
                .map(item -> item.getScene() + ": " + firstNonBlank(item.getErrorMessage(), "处理失败"))
                .findFirst()
                .orElse("资讯 AI 预览存在异常");
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
