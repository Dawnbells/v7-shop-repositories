package cn.v7soft.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderStatisticsQueryJobService {

    static final Duration JOB_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderStatisticsQueryJobService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this(redisTemplate, objectMapper, Clock.systemUTC());
    }

    OrderStatisticsQueryJobService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public OrderStatisticsQueryJob start(long companyId, long userId) {
        String activeKey = activeKey(companyId, userId);
        String previousJobId = redisTemplate.opsForValue().get(activeKey);
        if (previousJobId != null && !previousJobId.isBlank()) {
            cancel(companyId, userId, previousJobId);
        }

        String jobId = UUID.randomUUID().toString().replace("-", "");
        OrderStatisticsQueryJob job = OrderStatisticsQueryJob.processing(
                companyId,
                userId,
                jobId,
                clock.instant()
        );
        save(job);
        redisTemplate.opsForValue().set(activeKey, jobId, JOB_TTL);
        return job;
    }

    public OrderStatisticsQueryJob status(
            long companyId,
            long userId,
            String jobId
    ) {
        String value = redisTemplate.opsForValue().get(jobKey(companyId, userId, jobId));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("统计查询任务已过期");
        }
        OrderStatisticsQueryJob job = read(value);
        if (job.companyId() != companyId
                || job.userId() != userId
                || !jobId.equals(job.jobId())) {
            throw new IllegalArgumentException("统计查询任务已过期");
        }
        return job;
    }

    public OrderStatisticsQueryJob cancel(
            long companyId,
            long userId,
            String jobId
    ) {
        OrderStatisticsQueryJob current;
        try {
            current = status(companyId, userId, jobId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (current.state() != OrderStatisticsQueryJobState.PROCESSING) {
            return current;
        }
        OrderStatisticsQueryJob cancelled = new OrderStatisticsQueryJob(
                companyId,
                userId,
                jobId,
                OrderStatisticsQueryJobState.CANCELLED,
                current.createdAt(),
                clock.instant(),
                null,
                "已取消"
        );
        save(cancelled);
        clearActiveIfCurrent(companyId, userId, jobId);
        return cancelled;
    }

    public boolean complete(
            long companyId,
            long userId,
            String jobId,
            String resultToken
    ) {
        OrderStatisticsQueryJob current = status(companyId, userId, jobId);
        if (current.state() != OrderStatisticsQueryJobState.PROCESSING) {
            return false;
        }
        save(new OrderStatisticsQueryJob(
                companyId,
                userId,
                jobId,
                OrderStatisticsQueryJobState.COMPLETED,
                current.createdAt(),
                clock.instant(),
                resultToken,
                null
        ));
        clearActiveIfCurrent(companyId, userId, jobId);
        return true;
    }

    public void fail(
            long companyId,
            long userId,
            String jobId,
            String message
    ) {
        OrderStatisticsQueryJob current = status(companyId, userId, jobId);
        if (current.state() != OrderStatisticsQueryJobState.PROCESSING) {
            return;
        }
        save(new OrderStatisticsQueryJob(
                companyId,
                userId,
                jobId,
                OrderStatisticsQueryJobState.FAILED,
                current.createdAt(),
                clock.instant(),
                null,
                message
        ));
        clearActiveIfCurrent(companyId, userId, jobId);
    }

    public boolean isCancelled(long companyId, long userId, String jobId) {
        return status(companyId, userId, jobId).state()
                == OrderStatisticsQueryJobState.CANCELLED;
    }

    private void save(OrderStatisticsQueryJob job) {
        redisTemplate.opsForValue().set(
                jobKey(job.companyId(), job.userId(), job.jobId()),
                write(job),
                JOB_TTL
        );
    }

    private void clearActiveIfCurrent(long companyId, long userId, String jobId) {
        String key = activeKey(companyId, userId);
        String activeJobId = redisTemplate.opsForValue().get(key);
        if (jobId.equals(activeJobId)) {
            redisTemplate.delete(key);
        }
    }

    private String jobKey(long companyId, long userId, String jobId) {
        return "order-statistics:query-job:"
                + companyId + ":" + userId + ":" + jobId;
    }

    private String activeKey(long companyId, long userId) {
        return "order-statistics:active-query:" + companyId + ":" + userId;
    }

    private String write(OrderStatisticsQueryJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("统计查询任务序列化失败", exception);
        }
    }

    private OrderStatisticsQueryJob read(String value) {
        try {
            return objectMapper.readValue(value, OrderStatisticsQueryJob.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("统计查询任务反序列化失败", exception);
        }
    }
}
