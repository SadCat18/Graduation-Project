package com.skatehub.service.ai;

import com.skatehub.config.AiProperties;
import com.skatehub.pojo.ai.AiMessage;
import com.skatehub.pojo.ai.AiRequest;
import com.skatehub.pojo.ai.AiResponse;
import com.skatehub.provider.AiProvider;
import com.skatehub.util.BizException;
import com.skatehub.util.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGatewayService {

    public static final int MAX_PROMPT_CHARS = 20_000;
    public static final int MAX_TOKENS = 4_000;
    public static final int USER_RATE_LIMIT_PER_MINUTE = 30;

    private final AiProperties aiProperties;
    private final AiProviderRegistry aiProviderRegistry;
    private final ConcurrentMap<Long, RateWindow> userRateWindows = new ConcurrentHashMap<>();

    public AiResponse chat(AiRequest request) {
        validateRequestLimits(request);
        AiProperties.EndpointConfig resolvedConfig = resolveConfig(request);
        String providerName = resolvedConfig.getProvider();
        log.info("ai route resolved, scene={}, provider={}, model={}",
                request == null ? null : request.getScene(),
                providerName,
                resolvedConfig.getModel());
        AiProvider provider = aiProviderRegistry.getProvider(providerName);
        return provider.chat(request, resolvedConfig);
    }

    public AiResponse chatUserRequest(CurrentUser currentUser, AiRequest request) {
        if (!isAdmin(currentUser)) {
            rejectAdvancedUserOptions(request);
        }
        checkRateLimit(currentUser);
        return chat(request);
    }

    public void checkRateLimit(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null || isAdmin(currentUser)) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        RateWindow window = userRateWindows.compute(currentUser.id(), (userId, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= 60_000L) {
                return new RateWindow(now, 1);
            }
            existing.count++;
            return existing;
        });
        if (window.count > USER_RATE_LIMIT_PER_MINUTE) {
            throw new BizException("AI 调用过于频繁，请稍后再试");
        }
    }

    public AiProperties config() {
        return aiProperties;
    }

    public AiProperties.EndpointConfig resolveConfig(AiRequest request) {
        if (request == null) {
            return aiProperties.resolveSceneConfig(null);
        }
        return aiProperties.resolveRequestConfig(
                request.getScene(),
                normalize(request.getProvider()),
                normalize(request.getModel())
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateRequestLimits(AiRequest request) {
        if (request == null) {
            throw new BizException("AI 请求不能为空");
        }
        Integer maxTokens = request.getMaxTokens();
        if (maxTokens != null && maxTokens > MAX_TOKENS) {
            throw new BizException("maxTokens 不能超过 " + MAX_TOKENS);
        }
        if (promptLength(request) > MAX_PROMPT_CHARS) {
            throw new BizException("prompt 长度不能超过 " + MAX_PROMPT_CHARS);
        }
    }

    private int promptLength(AiRequest request) {
        int total = textLength(request.getSystemPrompt()) + textLength(request.getUserPrompt());
        for (AiMessage message : request.getMessages()) {
            if (message != null) {
                total += textLength(message.getContent());
            }
        }
        return total;
    }

    private int textLength(String value) {
        return StringUtils.hasText(value) ? value.trim().length() : 0;
    }

    private void rejectAdvancedUserOptions(AiRequest request) {
        if (request == null) {
            throw new BizException("AI 请求不能为空");
        }
        if (StringUtils.hasText(request.getProvider())
                || StringUtils.hasText(request.getModel())
                || StringUtils.hasText(request.getSystemPrompt())
                || Boolean.TRUE.equals(request.getWebSearchEnabled())
                || Boolean.TRUE.equals(request.getUseResponsesApi())) {
            throw new BizException("普通用户不能设置 AI 高级参数");
        }
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.role());
    }

    private static class RateWindow {
        private final long windowStartMillis;
        private int count;

        private RateWindow(long windowStartMillis, int count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
