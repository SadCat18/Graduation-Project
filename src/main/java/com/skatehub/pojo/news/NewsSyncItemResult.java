package com.skatehub.pojo.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSyncItemResult {

    private String originTitle;
    private String sourceName;
    private String sourceUrl;
    private boolean saved;
    private boolean duplicated;
    private boolean suspectedDuplicate;
    private boolean dryRun;
    private Long newsId;
    private String duplicateType;
    private String aiTitle;
    private String aiSummary;
    private String aiCategory;
    private String aiTranslatedContent;
    private String aiStatus;
    private String message;
}
