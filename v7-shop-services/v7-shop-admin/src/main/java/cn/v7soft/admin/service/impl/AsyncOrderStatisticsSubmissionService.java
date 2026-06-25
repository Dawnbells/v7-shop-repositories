package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Primary
@Service
public class AsyncOrderStatisticsSubmissionService
        extends OrderStatisticsSubmissionService {

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private final OrderStatisticsSnapshotService snapshotService;
    private final OrderStatisticsConfigService configService;
    private final ObjectMapper objectMapper;
    private final OrderStatisticsExecutionService executionService;
    private final OrderStatisticsQueryJobService jobService;
    private final Executor executor;
    private final Duration waitDuration;

    public AsyncOrderStatisticsSubmissionService(
            IOrderStatisticsService synchronousService,
            OrderStatisticsSnapshotService snapshotService,
            OrderStatisticsConfigService configService,
            ObjectMapper objectMapper,
            OrderStatisticsExecutionService executionService,
            OrderStatisticsQueryJobService jobService,
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor
    ) {
        this(
                synchronousService,
                snapshotService,
                configService,
                objectMapper,
                executionService,
                jobService,
                executor,
                DEFAULT_WAIT
        );
    }

    AsyncOrderStatisticsSubmissionService(
            IOrderStatisticsService synchronousService,
            OrderStatisticsSnapshotService snapshotService,
            OrderStatisticsConfigService configService,
            ObjectMapper objectMapper,
            OrderStatisticsExecutionService executionService,
            OrderStatisticsQueryJobService jobService,
            Executor executor,
            Duration waitDuration
    ) {
        super(synchronousService, snapshotService, configService, objectMapper);
        this.snapshotService = snapshotService;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.jobService = jobService;
        this.executor = executor;
        this.waitDuration = waitDuration;
    }

    @Override
    public OrderStatisticsQueryResponse submit(OrderStatisticsQueryRequest request) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        OrderStatisticsUserConfig config = configService.getOrCreate(null);
        OrderStatisticsExecutionContext context = new OrderStatisticsExecutionContext(
                user,
                SaSessionUtil.getViewMode(),
                WebsiteContext.isWebsiteAdmin(),
                WebsiteContext.getCurrentWebsiteId(),
                config
        );
        String fingerprint = fingerprint(request, user, config);

        if (!Boolean.TRUE.equals(request.getForceRefresh())) {
            try {
                String cachedToken = snapshotService.findCachedResultToken(
                        user.getCompanyId(),
                        user.getLongId(),
                        fingerprint
                );
                if (cachedToken != null && !cachedToken.isBlank()) {
                    try {
                        return completed(
                                snapshotService.get(
                                        user.getCompanyId(),
                                        user.getLongId(),
                                        cachedToken
                                ),
                                true
                        );
                    } catch (IllegalArgumentException ignored) {
                        // Recompute when the short cache points to an expired result.
                    }
                }
            } catch (DataAccessException exception) {
                return degraded(executionService.execute(request, context));
            }
        }

        OrderStatisticsQueryJob job;
        try {
            job = jobService.start(user.getCompanyId(), user.getLongId());
        } catch (DataAccessException exception) {
            return degraded(executionService.execute(request, context));
        }

        CompletableFuture<OrderStatisticsResultResponse> future =
                CompletableFuture.supplyAsync(
                        () -> executionService.execute(request, context),
                        executor
                );
        try {
            OrderStatisticsResultResponse result = future.get(
                    waitDuration.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            OrderStatisticsStoredSnapshot snapshot = persist(
                    user,
                    job.jobId(),
                    fingerprint,
                    result
            );
            if (snapshot == null) {
                return cancelled(job.jobId());
            }
            return completed(snapshot, false);
        } catch (TimeoutException exception) {
            future.whenComplete((result, error) -> {
                if (error != null) {
                    failQuietly(user, job.jobId(), error);
                    return;
                }
                try {
                    persist(user, job.jobId(), fingerprint, result);
                } catch (RuntimeException persistenceError) {
                    failQuietly(user, job.jobId(), persistenceError);
                }
            });
            return processing(job.jobId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            jobService.cancel(user.getCompanyId(), user.getLongId(), job.jobId());
            throw new IllegalStateException("订单统计查询被中断", exception);
        } catch (ExecutionException exception) {
            failQuietly(user, job.jobId(), exception.getCause());
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("订单统计查询失败", exception.getCause());
        }
    }

    private OrderStatisticsStoredSnapshot persist(
            SystemUserDto user,
            String jobId,
            String fingerprint,
            OrderStatisticsResultResponse result
    ) {
        if (jobService.isCancelled(user.getCompanyId(), user.getLongId(), jobId)) {
            return null;
        }
        OrderStatisticsStoredSnapshot snapshot = snapshotService.store(
                user.getCompanyId(),
                user.getLongId(),
                result
        );
        if (!jobService.complete(
                user.getCompanyId(),
                user.getLongId(),
                jobId,
                snapshot.resultToken()
        )) {
            return null;
        }
        snapshotService.cacheResultToken(
                user.getCompanyId(),
                user.getLongId(),
                fingerprint,
                snapshot.resultToken()
        );
        return snapshot;
    }

    private void failQuietly(SystemUserDto user, String jobId, Throwable error) {
        try {
            jobService.fail(
                    user.getCompanyId(),
                    user.getLongId(),
                    jobId,
                    rootMessage(error)
            );
        } catch (RuntimeException jobError) {
            log.warn("更新订单统计查询任务失败: jobId={}", jobId, jobError);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "订单统计查询失败" : current.getMessage();
    }

    private OrderStatisticsQueryResponse processing(String jobId) {
        return OrderStatisticsQueryResponse.builder()
                .state("PROCESSING")
                .queryJobId(jobId)
                .cached(false)
                .degraded(false)
                .build();
    }

    private OrderStatisticsQueryResponse cancelled(String jobId) {
        return OrderStatisticsQueryResponse.builder()
                .state("CANCELLED")
                .queryJobId(jobId)
                .cached(false)
                .degraded(false)
                .message("查询已取消")
                .build();
    }

    private OrderStatisticsQueryResponse completed(
            OrderStatisticsStoredSnapshot snapshot,
            boolean cached
    ) {
        return OrderStatisticsQueryResponse.builder()
                .state("COMPLETED")
                .resultToken(snapshot.resultToken())
                .snapshotExpiresAt(snapshot.expiresAt())
                .result(snapshot.result())
                .cached(cached)
                .degraded(false)
                .build();
    }

    private OrderStatisticsQueryResponse degraded(OrderStatisticsResultResponse result) {
        OrderStatisticsResultResponse limited = OrderStatisticsResultResponse.builder()
                .targetCurrencyCode(result.getTargetCurrencyCode())
                .summary(result.getSummary())
                .buckets(result.getBuckets())
                .groups(result.getGroups() == null
                        ? List.of()
                        : result.getGroups().stream().limit(100).toList())
                .originalCurrencies(result.getOriginalCurrencies())
                .missingRates(result.getMissingRates())
                .build();
        return OrderStatisticsQueryResponse.builder()
                .state("COMPLETED")
                .result(limited)
                .cached(false)
                .degraded(true)
                .message("Redis 暂不可用，已返回同步降级结果；分页和导出不可用")
                .build();
    }

    private String fingerprint(
            OrderStatisticsQueryRequest request,
            SystemUserDto user,
            OrderStatisticsUserConfig config
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", request);
        payload.put("companyId", user.getCompanyId());
        payload.put("userId", user.getId());
        payload.put("userType", user.getUserType());
        payload.put("departmentId", user.getDepartmentId());
        payload.put("accessDepartmentIds", user.getAccessDepartmentIds());
        payload.put("parentDepartmentIds", user.getParentDepartmentIds());
        payload.put("crossDepartment", user.getIsCrossDepartment());
        payload.put("manageDepartmentIds", user.getManageDepartmentIds());
        payload.put("excludeDepartment", user.getIsExcludeDepartment());
        payload.put("viewMode", SaSessionUtil.getViewMode());
        payload.put("websiteScoped", WebsiteContext.isWebsiteAdmin());
        payload.put("websiteId", WebsiteContext.getCurrentWebsiteId());
        payload.put("timeZoneId", config.getTimeZoneId());
        payload.put("personalExchangeRates", config.getExchangeRates());
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("订单统计查询指纹生成失败", exception);
        }
    }
}
