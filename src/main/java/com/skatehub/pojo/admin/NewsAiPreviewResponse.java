package com.skatehub.pojo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAiPreviewResponse {

    private String aiTitle;
    private String aiSummary;
    private String aiCategory;
    private String aiTranslatedContent;
    private String riskLevel;
    private String providerUsed;
    private String modelUsed;
    private String scene;
    private boolean success;
    private String message;
    private List<NewsAiRouteInfo> routes;
}
