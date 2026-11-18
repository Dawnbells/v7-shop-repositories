package cn.v7soft.admin.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GeminiQuotaTracker {

    private static final ZoneId PT_ZONE = ZoneId.of("America/Los_Angeles");
    // 旧计数按 Key 的前 8 位保存，无法可靠地归属到单个 Key。
    private static final String QUOTA_PREFIX = "gemini:quota:v2:";
    private static final String EXHAUSTED = "-1";
    private static final RedisScript<Long> RESERVE = RedisScript.of("""
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            if used < 0 then
                return -1
            end
            if used >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('INCR', KEYS[1])
            redis.call('EXPIREAT', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    private static final RedisScript<Long> REFUND = RedisScript.of("""
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            if used > 0 then
                return redis.call('DECR', KEYS[1])
            end
            return 0
            """, Long.class);

    private final int dailyLimitPerKey;
    private final List<String> apiKeys;
    private final StringRedisTemplate redis;

    public GeminiQuotaTracker(
            @Value("${gemini.daily-limit-per-key:${GEMINI_DAILY_LIMIT_PER_KEY:100000}}") int dailyLimitPerKey,
            @Value("${gemini.api-keys}") String apiKeysConfig,
            StringRedisTemplate redis) {
        this.dailyLimitPerKey = dailyLimitPerKey;
        this.apiKeys = Arrays.stream(apiKeysConfig.split(","))
                .map(String::strip)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
        this.redis = redis;
        log.info("[QuotaTracker] 初始化: keys={}, dailyLimitPerKey={}", apiKeys.size(), dailyLimitPerKey);
    }

    List<String> getApiKeys() {
        return apiKeys;
    }

    int getDailyLimitPerKey() {
        return dailyLimitPerKey;
    }

    Reservation reserve(String apiKey) {
        LocalDate quotaDate = LocalDate.now(PT_ZONE);
        Instant resetAt = quotaDate.plusDays(1).atStartOfDay(PT_ZONE).toInstant();
        String fingerprint = DigestUtil.sha256Hex(apiKey);
        String quotaKey = QUOTA_PREFIX + fingerprint + ":" + quotaDate;
        Long result = redis.execute(RESERVE, List.of(quotaKey), String.valueOf(dailyLimitPerKey),
                String.valueOf(resetAt.getEpochSecond()));
        if (result == null) {
            throw new IllegalStateException("Redis 未返回 Gemini 配额检查结果");
        }
        ReservationStatus status = switch (result.intValue()) {
            case 1 -> ReservationStatus.ACQUIRED;
            case 0 -> ReservationStatus.LOCAL_LIMIT;
            case -1 -> ReservationStatus.PROVIDER_LIMIT;
            default -> throw new IllegalStateException("Redis 返回的 Gemini 配额检查结果无效");
        };
        return new Reservation(quotaKey, resetAt, fingerprint, status);
    }

    void refund(Reservation reservation) {
        redis.execute(REFUND, List.of(reservation.quotaKey()));
    }

    void markExhausted(Reservation reservation) {
        Duration remaining = Duration.between(Instant.now(), reservation.resetAt());
        if (remaining.toMillis() > 0) {
            redis.opsForValue().set(reservation.quotaKey(), EXHAUSTED, remaining);
            log.warn("[QuotaTracker] Key[{}] 被 API 标记为每日配额耗尽，重置时间={}",
                    reservation.fingerprint().substring(0, 8), reservation.resetAt());
        }
    }

    enum ReservationStatus {
        ACQUIRED, LOCAL_LIMIT, PROVIDER_LIMIT
    }

    record Reservation(String quotaKey, Instant resetAt, String fingerprint, ReservationStatus status) {
    }
}
