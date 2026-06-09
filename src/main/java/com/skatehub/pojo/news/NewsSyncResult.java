package com.skatehub.pojo.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSyncResult {

    private int total;
    private int fetchedCount;
    private int saved;
    private int newCount;
    private int duplicated;
    private int suspectedDuplicate;
    private int failed;
    private boolean dryRun;
    private List<NewsSyncItemResult> items;
}
