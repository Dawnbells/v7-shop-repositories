package cn.v7soft.admin.service.impl;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsQueryJobServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private OrderStatisticsQueryJobService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new OrderStatisticsQueryJobService(
                redisTemplate,
                objectMapper,
                Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void startingNewJobCancelsPreviousActiveJob() throws Exception {
        OrderStatisticsQueryJob previous = OrderStatisticsQueryJob.processing(
                9L,
                101L,
                "old-job",
                Instant.parse("2026-06-25T11:55:00Z")
        );
        when(valueOperations.get("order-statistics:active-query:9:101"))
                .thenReturn("old-job");
        when(valueOperations.get("order-statistics:query-job:9:101:old-job"))
                .thenReturn(objectMapper.writeValueAsString(previous));

        OrderStatisticsQueryJob created = service.start(9L, 101L);

        assertThat(created.state()).isEqualTo(OrderStatisticsQueryJobState.PROCESSING);
        assertThat(created.jobId()).isNotEqualTo("old-job");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(
                anyString(),
                json.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30))
        );
        assertThat(json.getAllValues())
                .anySatisfy(value -> assertThat(value)
                        .contains("\"jobId\":\"old-job\"")
                        .contains("\"state\":\"CANCELLED\""));
    }

    @Test
    void cancelledJobCannotBeCompleted() throws Exception {
        OrderStatisticsQueryJob cancelled = new OrderStatisticsQueryJob(
                9L,
                101L,
                "job-1",
                OrderStatisticsQueryJobState.CANCELLED,
                Instant.parse("2026-06-25T11:55:00Z"),
                null,
                null,
                "已取消"
        );
        when(valueOperations.get("order-statistics:query-job:9:101:job-1"))
                .thenReturn(objectMapper.writeValueAsString(cancelled));

        boolean completed = service.complete(9L, 101L, "job-1", "token-1");

        assertThat(completed).isFalse();
        verify(valueOperations, never()).set(
                org.mockito.ArgumentMatchers.eq(
                        "order-statistics:query-job:9:101:job-1"
                ),
                org.mockito.ArgumentMatchers.contains("\"state\":\"COMPLETED\""),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void statusUsesOwnerBoundRedisKey() throws Exception {
        OrderStatisticsQueryJob completed = new OrderStatisticsQueryJob(
                9L,
                101L,
                "job-1",
                OrderStatisticsQueryJobState.COMPLETED,
                Instant.parse("2026-06-25T11:55:00Z"),
                Instant.parse("2026-06-25T12:00:00Z"),
                "token-1",
                null
        );
        when(valueOperations.get("order-statistics:query-job:9:101:job-1"))
                .thenReturn(objectMapper.writeValueAsString(completed));

        OrderStatisticsQueryJob result = service.status(9L, 101L, "job-1");

        assertThat(result.resultToken()).isEqualTo("token-1");
        verify(valueOperations).get("order-statistics:query-job:9:101:job-1");
    }
}
