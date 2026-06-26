package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncOrderStatisticsSubmissionServiceTest {

    @Mock
    private IOrderStatisticsService synchronousService;
    @Mock
    private OrderStatisticsSnapshotService snapshotService;
    @Mock
    private OrderStatisticsConfigService configService;
    @Mock
    private OrderStatisticsExecutionService executionService;
    @Mock
    private OrderStatisticsQueryJobService jobService;
    @Mock
    private ICompanyService companyService;
    @Mock
    private CurrencyRepository currencyRepository;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private MockedStatic<WebsiteContext> websiteContext;
    private ExecutorService executor;
    private AsyncOrderStatisticsSubmissionService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.registerModule(
                new com.fasterxml.jackson.databind.module.SimpleModule()
                        .addSerializer(
                                OrderStatisticsQueryRequest.class,
                                new cn.v7soft.admin.statistics
                                        .OrderStatisticsQueryFingerprintSerializer()
                        )
        );
        service = new AsyncOrderStatisticsSubmissionService(
                synchronousService,
                snapshotService,
                configService,
                objectMapper,
                currencyRepository,
                executionService,
                jobService,
                companyService,
                executor,
                Duration.ofMillis(20)
        );
        SystemUserDto user = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .userType(SystemUserType.COMPANY_ADMIN)
                .build();
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(user);
        saSessionUtil.when(SaSessionUtil::getViewMode).thenReturn(ViewMode.TEAM);
        websiteContext = mockStatic(WebsiteContext.class);
        websiteContext.when(WebsiteContext::isWebsiteAdmin).thenReturn(false);
        websiteContext.when(WebsiteContext::getCurrentWebsiteId).thenReturn(null);
        when(configService.getOrCreate(null)).thenReturn(config());
        when(currencyRepository.findAllValid()).thenReturn(List.of());
        when(snapshotService.findCachedResultToken(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(101L),
                anyString()
        )).thenReturn(null);
        when(jobService.start(9L, 101L)).thenReturn(OrderStatisticsQueryJob.processing(
                9L,
                101L,
                "job-1",
                Instant.parse("2026-06-25T12:00:00Z")
        ));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        websiteContext.close();
        saSessionUtil.close();
    }

    @Test
    void fastQueryReturnsCompletedSnapshot() {
        when(executionService.execute(any(), any())).thenReturn(result());
        when(snapshotService.store(9L, 101L, result())).thenReturn(snapshot());
        when(jobService.complete(9L, 101L, "job-1", "token-1")).thenReturn(true);

        OrderStatisticsQueryResponse response = service.submit(request());

        assertThat(response.getState()).isEqualTo("COMPLETED");
        assertThat(response.getResultToken()).isEqualTo("token-1");
        assertThat(response.getQueryJobId()).isNull();
        verify(executionService).execute(any(), any());
    }

    @Test
    void slowQueryReturnsJobAndSameFutureStoresResult() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(executionService.execute(any(), any())).thenAnswer(ignored -> {
            release.await();
            return result();
        });
        when(snapshotService.store(9L, 101L, result())).thenReturn(snapshot());
        when(jobService.complete(9L, 101L, "job-1", "token-1")).thenReturn(true);

        OrderStatisticsQueryResponse response = service.submit(request());

        assertThat(response.getState()).isEqualTo("PROCESSING");
        assertThat(response.getQueryJobId()).isEqualTo("job-1");
        release.countDown();
        verify(jobService, timeout(1000))
                .complete(9L, 101L, "job-1", "token-1");
        verify(executionService).execute(any(), any());
    }

    @Test
    void asyncExecutionRunsWithCompanyTenant() {
        // 回归：异步查询在工作线程上执行时必须重建公司租户，避免 @TenantId 查询解析为 root(-1) 跨公司泄露
        java.util.concurrent.atomic.AtomicReference<Long> tenantDuringExecute =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(executionService.execute(any(), any())).thenAnswer(invocation -> {
            tenantDuringExecute.set(TenantContext.getCurrentTenant());
            return result();
        });
        when(snapshotService.store(9L, 101L, result())).thenReturn(snapshot());
        when(jobService.complete(9L, 101L, "job-1", "token-1")).thenReturn(true);

        service.submit(request());

        assertThat(tenantDuringExecute.get()).isEqualTo(9L);
    }

    private OrderStatisticsExecutionContext executionContext() {
        return new OrderStatisticsExecutionContext(
                SaSessionUtil.getLoginUser(),
                ViewMode.TEAM,
                false,
                null,
                config()
        );
    }

    private OrderStatisticsUserConfig config() {
        return OrderStatisticsUserConfig.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("USD", "1"))
                .build();
    }

    private OrderStatisticsQueryRequest request() {
        return OrderStatisticsQueryRequest.builder()
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2026-06-01"))
                .granularity(OrderStatisticsGranularity.DAY)
                .dimension(OrderStatisticsDimension.EMPLOYEE)
                .employeeIds(List.of("101"))
                .targetCurrencyCode("USD")
                .build();
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

    private OrderStatisticsStoredSnapshot snapshot() {
        return new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "token-1",
                Instant.parse("2026-06-25T12:00:00Z"),
                Instant.parse("2026-06-25T12:30:00Z"),
                result()
        );
    }
}
