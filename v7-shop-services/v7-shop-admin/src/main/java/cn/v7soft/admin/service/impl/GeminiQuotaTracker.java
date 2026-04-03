package cn.v7soft.admin.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GeminiQuotaTracker {

    private static final ZoneId PT_ZONE = ZoneId.of("America/Los_Angeles");
    private static final String QUOTA_PREFIX = "gemini:quota:";

    private final int dailyLimitPerKey;
    private final List<String> apiKeys;
    private final StringRedisTemplate redis;

    public GeminiQuotaTracker(
            @Value("${gemini.daily-limit-per-key:1000}") int dailyLimitPerKey,
            @Value("${gemini.api-keys}") String apiKeysConfig,
            StringRedisTemplate redis) {
        this.dailyLimitPerKey = dailyLimitPerKey;
        this.apiKeys = List.of(apiKeysConfig.split(","));
        this.redis = redis;
        log.info("[QuotaTracker] 初始化: keys={}, dailyLimitPerKey={}", apiKeys.size(), dailyLimitPerKey);
    }

    public boolean increment(String apiKey) {
        String key = quotaKey(apiKey);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expireAt(key, ptMidnightInstant());
        }
        if (count != null && count > dailyLimitPerKey) {
            redis.opsForValue().decrement(key);
            return false;
        }
        return true;
    }

    public String tryAcquire() {
        for (String key : apiKeys) {
            if (increment(key)) {
                return key;
            }
        }
        return null;
    }

    public void decrement(String apiKey) {
        redis.opsForValue().decrement(quotaKey(apiKey));
    }

    public void markExhausted(String apiKey) {
        String key = quotaKey(apiKey);
        redis.opsForValue().set(key, String.valueOf(dailyLimitPerKey));
        redis.expireAt(key, ptMidnightInstant());
        log.warn("[QuotaTracker] Key[{}...] 被 API 标记为每日配额耗尽", maskKey(apiKey));
    }

    public boolean isExhausted(String apiKey) {
        return getUsedCount(apiKey) >= dailyLimitPerKey;
    }

    public boolean isAllExhausted() {
        for (String key : apiKeys) {
            if (!isExhausted(key)) return false;
        }
        return true;
    }

    public String getFirstAvailableKey() {
        for (String key : apiKeys) {
            if (!isExhausted(key)) return key;
        }
        return null;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public String getPrimaryKey() {
        return apiKeys.get(0);
    }

    public int getUsedCount(String apiKey) {
        String val = redis.opsForValue().get(quotaKey(apiKey));
        return val != null ? Integer.parseInt(val) : 0;
    }

    private String quotaKey(String apiKey) {
        return QUOTA_PREFIX + maskKey(apiKey) + ":" + LocalDate.now(PT_ZONE);
    }

    private Instant ptMidnightInstant() {
        return LocalDate.now(PT_ZONE).plusDays(1)
                .atStartOfDay(PT_ZONE).toInstant();
    }

    static String maskKey(String apiKey) {
        return apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey;
    }
}
