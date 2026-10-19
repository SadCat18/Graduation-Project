package com.skatehub.service;

import com.skatehub.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public RateLimitDecision allow(String key, int limit, Duration window) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.accepted();
        }
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, window);
            }
            if (count != null && count > limit) {
                log.warn("Rate limit exceeded for key={}", key);
                return RateLimitDecision.rejected(429, "Requests are too frequent. Please try again later.");
            }
            return RateLimitDecision.accepted();
        } catch (RedisConnectionFailureException exception) {
            log.error("Redis unavailable while checking rate limit key={}", key);
            if (properties.isRedisFailureDeniesProtected()) {
                return RateLimitDecision.rejected(503, "Rate limit service is temporarily unavailable.");
            }
            return RateLimitDecision.accepted();
        } catch (RuntimeException exception) {
            log.error("Rate limit check failed for key={}", key, exception);
            if (properties.isRedisFailureDeniesProtected()) {
                return RateLimitDecision.rejected(503, "Rate limit service is temporarily unavailable.");
            }
            return RateLimitDecision.accepted();
        }
    }

    public record RateLimitDecision(boolean allowed, int status, String message) {
        public static RateLimitDecision accepted() {
            return new RateLimitDecision(true, 200, "");
        }

        public static RateLimitDecision rejected(int status, String message) {
            return new RateLimitDecision(false, status, message);
        }
    }
}
