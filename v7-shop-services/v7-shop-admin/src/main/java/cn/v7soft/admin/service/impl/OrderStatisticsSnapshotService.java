package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderStatisticsSnapshotService {

    static final Duration RESULT_TTL = Duration.ofMinutes(30);
    static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderStatisticsSnapshotService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    OrderStatisticsSnapshotService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public OrderStatisticsStoredSnapshot store(
            long companyId,
            long userId,
            OrderStatisticsResultResponse result
    ) {
        Instant createdAt = clock.instant();
        String resultToken = UUID.randomUUID().toString().replace("-", "");
        OrderStatisticsStoredSnapshot snapshot = new OrderStatisticsStoredSnapshot(
                companyId,
                userId,
                resultToken,
                createdAt,
                createdAt.plus(RESULT_TTL),
                result
        );
        redisTemplate.opsForValue().set(
                resultKey(companyId, userId, resultToken),
                write(snapshot),
                RESULT_TTL
        );
        return snapshot;
    }

    public OrderStatisticsStoredSnapshot get(
            long companyId,
            long userId,
            String resultToken
    ) {
        if (resultToken == null || resultToken.isBlank()) {
            throw expired();
        }
        String value = redisTemplate.opsForValue().get(
                resultKey(companyId, userId, resultToken)
        );
        if (value == null || value.isBlank()) {
            throw expired();
        }
        OrderStatisticsStoredSnapshot snapshot = read(value);
        if (snapshot.companyId() != companyId
                || snapshot.userId() != userId
                || !resultToken.equals(snapshot.resultToken())
                || !snapshot.expiresAt().isAfter(clock.instant())) {
            throw expired();
        }
        return snapshot;
    }

    public void cacheResultToken(
            long companyId,
            long userId,
            String fingerprint,
            String resultToken
    ) {
        redisTemplate.opsForValue().set(
                cacheKey(companyId, userId, fingerprint),
                resultToken,
                CACHE_TTL
        );
    }

    public String findCachedResultToken(
            long companyId,
            long userId,
            String fingerprint
    ) {
        return redisTemplate.opsForValue().get(
                cacheKey(companyId, userId, fingerprint)
        );
    }

    private String resultKey(long companyId, long userId, String resultToken) {
        return "order-statistics:result:"
                + companyId + ":" + userId + ":" + resultToken;
    }

    private String cacheKey(long companyId, long userId, String fingerprint) {
        return "order-statistics:cache:"
                + companyId + ":" + userId + ":" + fingerprint;
    }

    private String write(OrderStatisticsStoredSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单统计快照序列化失败", exception);
        }
    }

    private OrderStatisticsStoredSnapshot read(String value) {
        try {
            return objectMapper.readValue(value, OrderStatisticsStoredSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("订单统计快照反序列化失败", exception);
        }
    }

    private IllegalArgumentException expired() {
        return new IllegalArgumentException("统计结果已过期，请重新查询");
    }
}
