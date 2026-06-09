package com.skatehub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * Unified provider identifier, e.g. ark / deepseek / openai / qwen.
     */
    private String provider = "ark";

    /**
     * Default model name for the active provider.
     */
    private String model;

    /**
     * Upstream compatible base URL.
     */
    private String baseUrl;

    /**
     * Upstream API key from environment variables or deployment config.
     */
    private String apiKey;

    /**
     * Explicit default config. Falls back to the legacy top-level provider/model/baseUrl/apiKey.
     */
    private EndpointConfig defaults = new EndpointConfig();

    /**
     * Shared config bucket for news-related AI scenes.
     */
    private EndpointConfig news = new EndpointConfig();

    /**
     * Optional per-scene overrides, e.g. ai.scenes.post_polish.model=xxx.
     */
    private Map<String, EndpointConfig> scenes = new LinkedHashMap<>();

    private static final Set<String> NEWS_SCENES = Set.of(
            "news_discovery",
            "news_summary",
            "news_translate",
            "news_classify",
            "news_title_polish"
    );

    public EndpointConfig resolveSceneConfig(String scene) {
        EndpointConfig resolved = merge(buildLegacyDefaultConfig(), defaults);
        if (isNewsScene(scene)) {
            resolved = merge(resolved, news);
        }
        EndpointConfig sceneOverride = findSceneOverride(scene);
        if (sceneOverride != null) {
            resolved = merge(resolved, sceneOverride);
        }
        return resolved;
    }

    public EndpointConfig resolveRequestConfig(String scene, String requestProvider, String requestModel) {
        EndpointConfig resolved = resolveSceneConfig(scene);
        if (StringUtils.hasText(requestProvider)) {
            resolved.setProvider(requestProvider.trim());
        }
        if (StringUtils.hasText(requestModel)) {
            resolved.setModel(requestModel.trim());
        }
        return resolved;
    }

    private EndpointConfig buildLegacyDefaultConfig() {
        EndpointConfig config = new EndpointConfig();
        config.setProvider(provider);
        config.setModel(model);
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        return config;
    }

    private boolean isNewsScene(String scene) {
        return StringUtils.hasText(scene)
                && NEWS_SCENES.contains(scene.trim().toLowerCase(Locale.ROOT));
    }

    private EndpointConfig findSceneOverride(String scene) {
        if (!StringUtils.hasText(scene) || scenes == null || scenes.isEmpty()) {
            return null;
        }
        String normalizedScene = scene.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, EndpointConfig> entry : scenes.entrySet()) {
            if (entry.getKey() != null
                    && normalizedScene.equals(entry.getKey().trim().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private EndpointConfig merge(EndpointConfig base, EndpointConfig override) {
        EndpointConfig merged = new EndpointConfig();
        merged.setProvider(firstNonBlank(base == null ? null : base.getProvider(),
                override == null ? null : override.getProvider()));
        merged.setModel(firstNonBlank(base == null ? null : base.getModel(),
                override == null ? null : override.getModel()));
        merged.setBaseUrl(firstNonBlank(base == null ? null : base.getBaseUrl(),
                override == null ? null : override.getBaseUrl()));
        merged.setApiKey(firstNonBlank(base == null ? null : base.getApiKey(),
                override == null ? null : override.getApiKey()));
        return merged;
    }

    private String firstNonBlank(String baseValue, String overrideValue) {
        return StringUtils.hasText(overrideValue) ? overrideValue.trim() : normalize(baseValue);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Data
    public static class EndpointConfig {

        private String provider;
        private String model;
        private String baseUrl;
        private String apiKey;

        public boolean hasApiKey() {
            return StringUtils.hasText(apiKey);
        }
    }
}
