package com.skatehub.service;

import com.skatehub.config.NewsSyncProperties;
import com.skatehub.dao.NewsRepository;
import com.skatehub.pojo.News;
import com.skatehub.pojo.ai.NewsAiStepResult;
import com.skatehub.pojo.ai.NewsClassificationAiResult;
import com.skatehub.pojo.ai.NewsSummaryAiResult;
import com.skatehub.pojo.ai.NewsTitlePolishAiResult;
import com.skatehub.pojo.ai.NewsTranslationAiResult;
import com.skatehub.pojo.news.NewsSyncItem;
import com.skatehub.pojo.news.NewsSyncItemResult;
import com.skatehub.pojo.news.NewsSyncResult;
import com.skatehub.service.ai.NewsAiService;
import com.skatehub.util.NewsContentSanitizer;
import com.skatehub.util.NewsAiProcessStatus;
import com.skatehub.util.NewsStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSyncService {

    private static final Long SYNC_ADMIN_ID = 0L;
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int TITLE_EXTENDED_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 20;
    private static final int SOURCE_NAME_MAX_LENGTH = 100;
    private static final int SOURCE_URL_MAX_LENGTH = 500;
    private static final int COVER_MAX_LENGTH = 255;
    private static final int AI_STATUS_MAX_LENGTH = 20;
    private static final int AI_ERROR_MAX_LENGTH = 500;
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");

    private final NewsRepository newsRepository;
    private final NewsAiService newsAiService;
    private final NewsSyncProperties newsSyncProperties;

    public NewsSyncResult sync(List<NewsSyncItem> items) {
        return sync(items, false);
    }

    public NewsSyncResult sync(List<NewsSyncItem> items, boolean dryRun) {
        List<NewsSyncItemResult> results = new ArrayList<>();
        int savedCount = 0;
        int duplicateCount = 0;
        int suspectedDuplicateCount = 0;
        int failedCount = 0;

        List<NewsSyncItem> safeItems = items == null ? List.of() : items;
        for (NewsSyncItem item : safeItems) {
            NewsSyncItemResult result = syncSingle(item, dryRun);
            results.add(result);
            if (result.isDuplicated()) {
                duplicateCount++;
            } else if (result.isSaved()) {
                savedCount++;
            } else {
                failedCount++;
            }
            if (result.isSuspectedDuplicate()) {
                suspectedDuplicateCount++;
            }
        }

        return NewsSyncResult.builder()
                .total(results.size())
                .fetchedCount(safeItems.size())
                .saved(savedCount)
                .newCount(savedCount)
                .duplicated(duplicateCount)
                .suspectedDuplicate(suspectedDuplicateCount)
                .failed(failedCount)
                .dryRun(dryRun)
                .items(results)
                .build();
    }

    public NewsSyncItemResult syncSingle(NewsSyncItem item, boolean dryRun) {
        String originTitle = NewsContentSanitizer.clean(item == null ? null : item.getTitle());
        String originContent = NewsContentSanitizer.clean(item == null ? null : item.getContent());
        String sourceName = safeText(item == null ? null : item.getSourceName());
        String sourceUrl = safeText(item == null ? null : item.getSourceUrl());

        if (!StringUtils.hasText(originTitle) || !StringUtils.hasText(originContent)) {
            NewsSyncItemResult result = baseItemResult(item, originTitle, sourceName, sourceUrl);
            result.setSaved(false);
            result.setDuplicated(false);
            result.setDryRun(dryRun);
            result.setAiStatus(NewsAiProcessStatus.SKIPPED);
            result.setMessage("标题或正文为空，已跳过");
            return result;
        }

        Optional<News> duplicated = findDuplicate(originTitle, originContent, sourceUrl);
        if (duplicated.isPresent()) {
            NewsSyncItemResult result = baseItemResult(item, originTitle, sourceName, sourceUrl);
            result.setSaved(false);
            result.setDuplicated(true);
            result.setDryRun(dryRun);
            result.setNewsId(duplicated.get().getNewsId());
            result.setDuplicateType(StringUtils.hasText(sourceUrl) && sourceUrl.equals(safeText(duplicated.get().getSourceUrl()))
                    ? "SOURCE_URL"
                    : "TITLE_CONTENT");
            result.setAiStatus(defaultText(duplicated.get().getAiStatus()));
            result.setMessage("资讯已存在，跳过入库");
            return result;
        }

        Optional<News> suspectedDuplicate = findSuspectedDuplicate(originTitle, sourceUrl);
        try {
            ProcessedNews processedNews = buildProcessedNews(item, originTitle, originContent);
            NewsSyncItemResult result = baseItemResult(item, originTitle, sourceName, sourceUrl);
            result.setDryRun(dryRun);
            result.setDuplicated(false);
            result.setSuspectedDuplicate(suspectedDuplicate.isPresent());
            result.setDuplicateType(suspectedDuplicate.isPresent() ? "TITLE_ONLY" : "");
            result.setAiTitle(processedNews.news().getAiTitle());
            result.setAiSummary(processedNews.news().getAiSummary());
            result.setAiCategory(processedNews.news().getAiCategory());
            result.setAiTranslatedContent(processedNews.news().getAiTranslatedContent());
            result.setAiStatus(processedNews.news().getAiStatus());

            if (dryRun) {
                result.setSaved(false);
                result.setMessage("dryRun 检测完成，未入库");
                return result;
            }

            News savedNews = newsRepository.save(processedNews.news());
            result.setSaved(true);
            result.setNewsId(savedNews.getNewsId());
            result.setMessage(processedNews.message());
            return result;
        } catch (Exception exception) {
            log.warn("news sync save failed, title={}", originTitle, exception);
            NewsSyncItemResult result = baseItemResult(item, originTitle, sourceName, sourceUrl);
            result.setSaved(false);
            result.setDuplicated(false);
            result.setDryRun(dryRun);
            result.setAiStatus(NewsAiProcessStatus.FAILED);
            result.setMessage("入库失败: " + exception.getMessage());
            return result;
        }
    }

    private ProcessedNews buildProcessedNews(NewsSyncItem item, String originTitle, String originContent) {
        boolean needsTranslation = shouldTranslate(originTitle, originContent);

        NewsAiStepResult<NewsTranslationAiResult> translationStep = needsTranslation
                ? newsAiService.translateNewsStep(originTitle, originContent)
                : NewsAiStepResult.<NewsTranslationAiResult>builder()
                .scene(NewsAiService.SCENE_TRANSLATE)
                .provider("")
                .model("")
                .success(true)
                .data(NewsTranslationAiResult.builder()
                        .translatedTitle(originTitle)
                        .translatedContent(originContent)
                        .build())
                .build();

        String aiBaseTitle = firstNonBlank(translationStep.getData() == null ? null : translationStep.getData().getTranslatedTitle(), originTitle);
        String aiBaseContent = firstNonBlank(translationStep.getData() == null ? null : translationStep.getData().getTranslatedContent(), originContent);

        NewsAiStepResult<NewsSummaryAiResult> summaryStep = newsAiService.summarizeNewsStep(aiBaseTitle, aiBaseContent);
        NewsAiStepResult<NewsClassificationAiResult> classificationStep = newsAiService.classifyNewsStep(aiBaseTitle, aiBaseContent);
        NewsAiStepResult<NewsTitlePolishAiResult> titleStep = newsAiService.polishNewsTitleStep(aiBaseTitle, aiBaseContent);

        List<NewsAiStepResult<?>> steps = List.of(translationStep, summaryStep, classificationStep, titleStep);
        String aiStatus = resolveAiStatus(steps);
        String aiErrorMessage = joinErrors(steps);

        News news = new News();
        news.setOriginTitle(limitLength(originTitle, TITLE_EXTENDED_MAX_LENGTH));
        news.setOriginContent(originContent);
        news.setOriginSummary(NewsContentSanitizer.clean(item == null ? null : item.getSummary()));
        news.setAiTranslatedContent(needsTranslation ? safeText(aiBaseContent) : "");
        news.setAiSummary(safeText(summaryStep.getData() == null ? null : summaryStep.getData().getSummary()));
        news.setAiCategory(resolveCategory(
                classificationStep.getData() == null ? null : classificationStep.getData().getCategory(),
                item == null ? null : item.getCategory()
        ));
        news.setAiTitle(limitLength(firstNonBlank(titleStep.getData() == null ? null : titleStep.getData().getTitle(), aiBaseTitle), TITLE_EXTENDED_MAX_LENGTH));
        news.setTitle(limitLength(firstNonBlank(news.getAiTitle(), aiBaseTitle, originTitle), TITLE_MAX_LENGTH));
        news.setContent(firstNonBlank(aiBaseContent, originContent));
        news.setSummary(firstNonBlank(news.getAiSummary(), safeText(item == null ? null : item.getSummary())));
        news.setCategory(limitLength(firstNonBlank(news.getAiCategory(), safeText(item == null ? null : item.getCategory())), CATEGORY_MAX_LENGTH));
        news.setCover(limitLength(safeText(item == null ? null : item.getCover()), COVER_MAX_LENGTH));
        news.setSourceName(limitLength(safeText(item == null ? null : item.getSourceName()), SOURCE_NAME_MAX_LENGTH));
        news.setSourceUrl(limitLength(safeText(item == null ? null : item.getSourceUrl()), SOURCE_URL_MAX_LENGTH));
        news.setStatus(newsSyncProperties.isAutoPublish() ? NewsStatus.APPROVED : NewsStatus.PENDING);
        news.setAiStatus(limitLength(aiStatus, AI_STATUS_MAX_LENGTH));
        news.setAiErrorMessage(blankToNull(limitLength(aiErrorMessage, AI_ERROR_MAX_LENGTH)));
        news.setAdminId(SYNC_ADMIN_ID);
        news.setSyncTime(LocalDateTime.now());
        return new ProcessedNews(news, buildProcessedMessage(aiStatus));
    }

    private Optional<News> findDuplicate(String originTitle, String originContent, String sourceUrl) {
        if (StringUtils.hasText(sourceUrl)) {
            Optional<News> byUrl = newsRepository.findFirstBySourceUrl(sourceUrl.trim());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }
        Optional<News> duplicated = newsRepository.findFirstByOriginTitleAndOriginContent(originTitle, originContent);
        if (duplicated.isPresent()) {
            return duplicated;
        }
        return newsRepository.findFirstByTitleAndContent(originTitle, originContent);
    }

    private Optional<News> findSuspectedDuplicate(String originTitle, String sourceUrl) {
        if (!StringUtils.hasText(originTitle)) {
            return Optional.empty();
        }
        Optional<News> sameTitle = newsRepository.findFirstByOriginTitle(originTitle.trim());
        if (sameTitle.isEmpty()) {
            return Optional.empty();
        }
        String existingUrl = safeText(sameTitle.get().getSourceUrl());
        if (!StringUtils.hasText(sourceUrl) || !sourceUrl.trim().equals(existingUrl)) {
            return sameTitle;
        }
        return Optional.empty();
    }

    private boolean shouldTranslate(String title, String content) {
        return isForeignDominant(title) || isForeignDominant(content);
    }

    private String resolveAiStatus(List<NewsAiStepResult<?>> steps) {
        long successCount = steps.stream().filter(NewsAiStepResult::isSuccess).count();
        if (successCount == steps.size()) {
            return NewsAiProcessStatus.SUCCESS;
        }
        if (successCount == 0) {
            return NewsAiProcessStatus.FAILED;
        }
        return NewsAiProcessStatus.PARTIAL;
    }

    private String joinErrors(List<NewsAiStepResult<?>> steps) {
        StringBuilder builder = new StringBuilder();
        for (NewsAiStepResult<?> step : steps) {
            if (step.isSuccess() || !StringUtils.hasText(step.getErrorMessage())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(step.getScene()).append(": ").append(step.getErrorMessage().trim());
        }
        return builder.toString();
    }

    private String buildProcessedMessage(String aiStatus) {
        if (NewsAiProcessStatus.SUCCESS.equals(aiStatus)) {
            return newsSyncProperties.isAutoPublish() ? "同步成功，已自动发布" : "同步成功，待管理员审核";
        }
        if (NewsAiProcessStatus.PARTIAL.equals(aiStatus)) {
            return "同步成功，但 AI 处理部分失败，已保留原始数据待审核";
        }
        return "同步成功，但 AI 处理失败，已保留原始数据待审核";
    }

    private String normalizeCategory(String aiCategory, String fallbackCategory) {
        if (StringUtils.hasText(aiCategory)) {
            return aiCategory.trim();
        }
        String fallback = safeText(fallbackCategory);
        return StringUtils.hasText(fallback) ? fallback : "未分类";
    }

    private NewsSyncItemResult baseItemResult(
            NewsSyncItem item,
            String originTitle,
            String sourceName,
            String sourceUrl) {
        NewsSyncItemResult result = new NewsSyncItemResult();
        result.setOriginTitle(originTitle);
        result.setSourceName(sourceName);
        result.setSourceUrl(sourceUrl);
        result.setAiSummary(safeText(item == null ? null : item.getSummary()));
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isForeignDominant(String text) {
        String normalized = safeText(text);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        int chineseCount = countMatches(CHINESE_PATTERN, normalized);
        int latinCount = countMatches(LATIN_PATTERN, normalized);
        if (latinCount == 0) {
            return false;
        }
        if (chineseCount == 0) {
            return true;
        }
        return latinCount >= chineseCount * 2;
    }

    private int countMatches(Pattern pattern, String text) {
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String resolveCategory(String aiCategory, String fallbackCategory) {
        if (StringUtils.hasText(aiCategory)) {
            return limitLength(aiCategory.trim(), CATEGORY_MAX_LENGTH);
        }
        String fallback = safeText(fallbackCategory);
        return StringUtils.hasText(fallback)
                ? limitLength(fallback, CATEGORY_MAX_LENGTH)
                : "未分类";
    }

    private String limitLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private record ProcessedNews(News news, String message) {
    }
}
