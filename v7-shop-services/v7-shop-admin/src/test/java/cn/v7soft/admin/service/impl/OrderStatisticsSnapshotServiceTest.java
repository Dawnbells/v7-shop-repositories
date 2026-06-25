package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsSnapshotServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private OrderStatisticsSnapshotService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new OrderStatisticsSnapshotService(
                redisTemplate,
                objectMapper,
                Clock.fixed(Instant.parse("2026-06-24T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void storesResultWithOwnerBoundKeyAndFixedThirtyMinuteTtl() {
        OrderStatisticsStoredSnapshot snapshot = service.store(9L, 101L, result());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                key.capture(),
                json.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30))
        );
        assertThat(key.getValue()).isEqualTo(
                "order-statistics:result:9:101:" + snapshot.resultToken()
        );
        assertThat(snapshot.expiresAt())
                .isEqualTo(Instant.parse("2026-06-24T12:30:00Z"));
        assertThat(json.getValue()).contains("\"companyId\":9", "\"userId\":101");
    }

    @Test
    void readsSnapshotWithoutSlidingExpiry() throws Exception {
        OrderStatisticsStoredSnapshot stored = new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "token-1",
                Instant.parse("2026-06-24T12:00:00Z"),
                Instant.parse("2026-06-24T12:30:00Z"),
                result()
        );
        when(valueOperations.get("order-statistics:result:9:101:token-1"))
                .thenReturn(objectMapper.writeValueAsString(stored));

        OrderStatisticsStoredSnapshot loaded = service.get(9L, 101L, "token-1");

        assertThat(loaded.result().getTargetCurrencyCode()).isEqualTo("USD");
        verify(redisTemplate, never()).expire(
                anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void rejectsMissingOrExpiredToken() throws Exception {
        when(valueOperations.get("order-statistics:result:9:101:missing")).thenReturn(null);
        OrderStatisticsStoredSnapshot expired = new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "expired",
                Instant.parse("2026-06-24T11:00:00Z"),
                Instant.parse("2026-06-24T11:30:00Z"),
                result()
        );
        when(valueOperations.get("order-statistics:result:9:101:expired"))
                .thenReturn(objectMapper.writeValueAsString(expired));

        assertThatThrownBy(() -> service.get(9L, 101L, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
        assertThatThrownBy(() -> service.get(9L, 101L, "expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void storesQueryCacheForOneMinute() {
        service.cacheResultToken(9L, 101L, "fingerprint", "token-1");

        verify(valueOperations).set(
                "order-statistics:cache:9:101:fingerprint",
                "token-1",
                Duration.ofMinutes(1)
        );
    }

    private OrderStatisticsResultResponse result() {
        return OrderStatisticsResultResponse.builder()
                .targetCurrencyCode("USD")
                .summary(OrderStatisticsMetricsResponse.builder()
                        .orderCount(1)
                        .build())
                .buckets(List.of())
                .groups(List.of())
                .originalCurrencies(List.of())
                .missingRates(List.of())
                .build();
    }
}
