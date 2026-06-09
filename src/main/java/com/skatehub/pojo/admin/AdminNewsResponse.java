package com.skatehub.pojo.admin;

import com.skatehub.pojo.News;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNewsResponse {

    private Long newsId;
    private String title;
    private String content;
    private String summary;
    private String cover;
    private String category;
    private String originTitle;
    private String originContent;
    private String originSummary;
    private String aiTitle;
    private String aiSummary;
    private String aiCategory;
    private String aiTranslatedContent;
    private String status;
    private String aiStatus;
    private String aiErrorMessage;
    private String sourceName;
    private String sourceUrl;
    private LocalDateTime syncTime;
    private LocalDateTime createTime;

    public static AdminNewsResponse from(News news) {
        return AdminNewsResponse.builder()
                .newsId(news.getNewsId())
                .title(news.getTitle())
                .content(news.getContent())
                .summary(news.getSummary())
                .cover(news.getCover())
                .category(news.getCategory())
                .originTitle(firstNonBlank(news.getOriginTitle(), news.getTitle()))
                .originContent(firstNonBlank(news.getOriginContent(), news.getContent()))
                .originSummary(firstNonBlank(news.getOriginSummary(), news.getSummary()))
                .aiTitle(news.getAiTitle())
                .aiSummary(news.getAiSummary())
                .aiCategory(news.getAiCategory())
                .aiTranslatedContent(news.getAiTranslatedContent())
                .status(news.getStatus())
                .aiStatus(news.getAiStatus())
                .aiErrorMessage(news.getAiErrorMessage())
                .sourceName(news.getSourceName())
                .sourceUrl(news.getSourceUrl())
                .syncTime(news.getSyncTime())
                .createTime(news.getCreateTime())
                .build();
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : "";
    }
}
