package com.skatehub.service;

import com.skatehub.dao.NewsRepository;
import com.skatehub.pojo.News;
import com.skatehub.pojo.admin.AdminNewsResponse;
import com.skatehub.pojo.admin.NewsAiReprocessBatchResponse;
import com.skatehub.pojo.admin.NewsAiReprocessItemResponse;
import com.skatehub.pojo.ai.NewsAiStepResult;
import com.skatehub.pojo.ai.NewsClassificationAiResult;
import com.skatehub.pojo.ai.NewsSummaryAiResult;
import com.skatehub.pojo.ai.NewsTitlePolishAiResult;
import com.skatehub.pojo.ai.NewsTranslationAiResult;
import com.skatehub.service.ai.NewsAiService;
import com.skatehub.util.NewsAiProcessStatus;
import com.skatehub.util.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsAiReprocessService {

    private static final int TITLE_EXTENDED_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 20;
    private static final int AI_STATUS_MAX_LENGTH = 20;
    private static final int AI_ERROR_MAX_LENGTH = 500;
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern LATIN_PATTERN = Pattern.compile("[A-Za-z]");

    private final NewsRepository newsRepository;
    private final NewsAiService newsAiService;

    public AdminNewsResponse reprocessSingle(Long newsId) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        NewsAiReprocessItemResponse result = reprocessNews(news);
        if (!result.isSuccess() && !result.isSkipped()) {
            throw new BizException(result.getMessage());
        }
        News refreshed = newsRepository.findById(newsId).orElseThrow(() -> new BizException("资讯不存在"));
        return AdminNewsResponse.from(refreshed);
    }

    public NewsAiReprocessBatchResponse reprocessBatch(List<Long> newsIds) {
        List<News> targets;
        if (CollectionUtils.isEmpty(newsIds)) {
            targets = newsRepository.findAllByOrderByCreateTimeDesc();
        } else {
            targets = newsRepository.findAllById(newsIds);
        }

        List<NewsAiReprocessItemResponse> items = new ArrayList<>();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (News news : targets) {
            NewsAiReprocessItemResponse item = reprocessNews(news);
            items.add(item);
            if (item.isSuccess()) {
                success++;
            } else if (item.isSkipped()) {
                skipped++;
            } else {
                failed++;
            }
        }

        return NewsAiReprocessBatchResponse.builder()
                .total(items.size())
                .success(success)
                .skipped(skipped)
                .failed(failed)
                .items(items)
                .build();
    }

    private NewsAiReprocessItemResponse reprocessNews(News news) {
        if (news == null || news.getNewsId() == null) {
            return NewsAiReprocessItemResponse.builder()
                    .newsId(null)
                    .success(false)
                    .skipped(true)
                    .message("资讯不存在，已跳过")
                    .build();
        }

        String sourceTitle = firstNonBlank(news.getOriginTitle(), news.getTitle());
        String sourceContent = firstNonBlank(news.getOriginContent(), news.getContent());
        if (!StringUtils.hasText(sourceTitle) && !StringUtils.hasText(sourceContent)) {
            log.info("skip news ai reprocess, newsId={}, reason=no usable title/content", news.getNewsId());
            return NewsAiReprocessItemResponse.builder()
                    .newsId(news.getNewsId())
                    .success(false)
                    .skipped(true)
                    .message("缺少可处理的标题和正文，已跳过")
                    .build();
        }

        try {
            String aiPromptContent = StringUtils.hasText(sourceContent) ? sourceContent : sourceTitle;
            boolean needsTranslation = shouldTranslate(sourceTitle, aiPromptContent);

            NewsAiStepResult<NewsTranslationAiResult> translationStep = needsTranslation
                    ? newsAiService.translateNewsStep(sourceTitle, aiPromptContent)
                    : NewsAiStepResult.<NewsTranslationAiResult>builder()
                    .scene(NewsAiService.SCENE_TRANSLATE)
                    .success(true)
                    .data(NewsTranslationAiResult.builder()
                            .translatedTitle(sourceTitle)
                            .translatedContent(aiPromptContent)
                            .build())
                    .build();

            String aiBaseTitle = firstNonBlank(translationStep.getData() == null ? null : translationStep.getData().getTranslatedTitle(), sourceTitle);
            String aiBaseContent = firstNonBlank(translationStep.getData() == null ? null : translationStep.getData().getTranslatedContent(), aiPromptContent);

            NewsAiStepResult<NewsSummaryAiResult> summaryStep = newsAiService.summarizeNewsStep(aiBaseTitle, aiBaseContent);
            NewsAiStepResult<NewsClassificationAiResult> classificationStep = newsAiService.classifyNewsStep(aiBaseTitle, aiBaseContent);
            NewsAiStepResult<NewsTitlePolishAiResult> titleStep = newsAiService.polishNewsTitleStep(aiBaseTitle, aiBaseContent);

            news.setAiTitle(blankToNull(limitLength(firstNonBlank(
                    titleStep.getData() == null ? null : titleStep.getData().getTitle(),
                    aiBaseTitle
            ), TITLE_EXTENDED_MAX_LENGTH)));
            news.setAiSummary(blankToNull(summaryStep.getData() == null ? null : summaryStep.getData().getSummary()));
            news.setAiCategory(blankToNull(limitLength(
                    firstNonBlank(classificationStep.getData() == null ? null : classificationStep.getData().getCategory()),
                    CATEGORY_MAX_LENGTH
            )));
            news.setAiTranslatedContent(needsTranslation && StringUtils.hasText(sourceContent)
                    ? blankToNull(aiBaseContent)
                    : null);
            long successCount = java.util.List.of(translationStep, summaryStep, classificationStep, titleStep).stream()
                    .filter(NewsAiStepResult::isSuccess)
                    .count();
            if (successCount == 4) {
                news.setAiStatus(limitLength(NewsAiProcessStatus.SUCCESS, AI_STATUS_MAX_LENGTH));
                news.setAiErrorMessage(null);
            } else if (successCount == 0) {
                news.setAiStatus(limitLength(NewsAiProcessStatus.FAILED, AI_STATUS_MAX_LENGTH));
                news.setAiErrorMessage(blankToNull(limitLength(
                        joinErrors(translationStep, summaryStep, classificationStep, titleStep),
                        AI_ERROR_MAX_LENGTH
                )));
            } else {
                news.setAiStatus(limitLength(NewsAiProcessStatus.PARTIAL, AI_STATUS_MAX_LENGTH));
                news.setAiErrorMessage(blankToNull(limitLength(
                        joinErrors(translationStep, summaryStep, classificationStep, titleStep),
                        AI_ERROR_MAX_LENGTH
                )));
            }
            newsRepository.save(news);

            log.info("news ai reprocess success, newsId={}", news.getNewsId());
            return NewsAiReprocessItemResponse.builder()
                    .newsId(news.getNewsId())
                    .success(true)
                    .skipped(false)
                    .message("AI 重跑成功")
                    .build();
        } catch (Exception exception) {
            log.warn("news ai reprocess failed, newsId={}", news.getNewsId(), exception);
            return NewsAiReprocessItemResponse.builder()
                    .newsId(news.getNewsId())
                    .success(false)
                    .skipped(false)
                    .message("AI 重跑失败: " + exception.getMessage())
                    .build();
        }
    }

    private boolean shouldTranslate(String title, String content) {
        return isForeignDominant(title) || isForeignDominant(content);
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

    private String joinErrors(NewsAiStepResult<?>... steps) {
        StringBuilder builder = new StringBuilder();
        for (NewsAiStepResult<?> step : steps) {
            if (step == null || step.isSuccess() || !StringUtils.hasText(step.getErrorMessage())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(step.getScene()).append(": ").append(step.getErrorMessage().trim());
        }
        return builder.toString();
    }
}
