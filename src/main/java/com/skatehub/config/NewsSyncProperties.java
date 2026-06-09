package com.skatehub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "news-sync")
public class NewsSyncProperties {

    /**
     * Whether manual news sync is enabled.
     */
    private boolean enabled = true;

    /**
     * External RSS/Atom source list for one-click admin sync.
     */
    private List<SourceConfig> sources = new ArrayList<>();

    /**
     * Keep recently published source items instead of only today's items.
     */
    private int lookbackDays = 7;

    /**
     * Whether synced news should be auto approved.
     */
    private boolean autoPublish = false;

    /**
     * Optional AI-powered web search supplement source.
     */
    private AiSearchConfig aiSearch = new AiSearchConfig();

    @Data
    public static class SourceConfig {
        private Integer id;
        private String name;
        private String url;
    }

    @Data
    public static class AiSearchConfig {
        private boolean enabled = false;
        private int maxItems = 6;
        private Integer sourceId = 999;
        private String sourceName = "AI 全网搜索";
        private String sourceUrl = "ai://ark/web-search";
    }
}
