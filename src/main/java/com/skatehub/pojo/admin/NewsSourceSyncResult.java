package com.skatehub.pojo.admin;

import com.skatehub.pojo.news.NewsSyncItemResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSourceSyncResult {

    private Integer sourceId;
    private String sourceName;
    private String sourceUrl;
    private int fetchedCount;
    private int newCount;
    private int duplicateCount;
    private int failedCount;
    private boolean success;
    private String message;
    private List<NewsSyncItemResult> items;
}
