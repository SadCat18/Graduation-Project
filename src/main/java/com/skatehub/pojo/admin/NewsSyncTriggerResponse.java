package com.skatehub.pojo.admin;

import com.skatehub.pojo.news.NewsSyncResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSyncTriggerResponse {

    private String taskId;
    private boolean running;
    private boolean completed;
    private boolean enabled;
    private boolean dryRun;
    private int sourceCount;
    private int fetchedCount;
    private int newCount;
    private int duplicateCount;
    private int failedCount;
    private List<String> sourceNames;
    private List<NewsSourceSyncResult> sourceResults;
    private NewsSyncResult result;
    private String message;
}
