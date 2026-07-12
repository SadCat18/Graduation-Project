package com.skatehub.actuator;

import com.skatehub.config.AiProperties;
import com.skatehub.service.FileStorageService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SkateHubHealthIndicator implements HealthIndicator {

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<FileStorageService> fileStorageProvider;
    private final ObjectProvider<AiProperties> aiPropertiesProvider;

    public SkateHubHealthIndicator() {
        this(null, null, null, null);
    }

    public SkateHubHealthIndicator(ObjectProvider<DataSource> dataSourceProvider,
                                   ObjectProvider<StringRedisTemplate> redisProvider,
                                   ObjectProvider<FileStorageService> fileStorageProvider,
                                   ObjectProvider<AiProperties> aiPropertiesProvider) {
        this.dataSourceProvider = dataSourceProvider;
        this.redisProvider = redisProvider;
        this.fileStorageProvider = fileStorageProvider;
        this.aiPropertiesProvider = aiPropertiesProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("database", checkDatabase());
        dependencies.put("redis", checkRedis());
        dependencies.put("fileStorage", checkFileStorage());
        dependencies.put("aiProviderConfig", checkAiProviderConfig());
        boolean degraded = dependencies.values().stream().anyMatch("DOWN"::equals);
        Health.Builder builder = degraded ? Health.status("DEGRADED") : Health.up();
        return builder
                .withDetail("module", "skatehub-core")
                .withDetail("p1Observability", "enabled")
                .withDetail("dependencies", dependencies)
                .build();
    }

    private String checkDatabase() {
        if (dataSourceProvider == null) {
            return "UNKNOWN";
        }
        try {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource == null) return "UNKNOWN";
            try (Connection connection = dataSource.getConnection()) {
                return connection.isValid(2) ? "UP" : "DOWN";
            }
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String checkRedis() {
        if (redisProvider == null) {
            return "UNKNOWN";
        }
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null || redis.getConnectionFactory() == null) return "UNKNOWN";
            try (var connection = redis.getConnectionFactory().getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "DOWN";
            }
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String checkFileStorage() {
        if (fileStorageProvider == null) {
            return "UNKNOWN";
        }
        try {
            FileStorageService storage = fileStorageProvider.getIfAvailable();
            if (storage == null) return "UNKNOWN";
            Files.createDirectories(storage.uploadRoot());
            return Files.isWritable(storage.uploadRoot()) ? "UP" : "DOWN";
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String checkAiProviderConfig() {
        if (aiPropertiesProvider == null) {
            return "UNKNOWN";
        }
        AiProperties properties = aiPropertiesProvider.getIfAvailable();
        if (properties == null) {
            return "UNKNOWN";
        }
        AiProperties.EndpointConfig config = properties.resolveSceneConfig("coach_chat");
        return config != null
                && StringUtils.hasText(config.getBaseUrl())
                && StringUtils.hasText(config.getApiKey())
                && StringUtils.hasText(config.getModel())
                ? "UP"
                : "DOWN";
    }
}
