package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
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
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsSubmissionServiceTest {

    @Mock
    private IOrderStatisticsService statisticsService;
    @Mock
    private OrderStatisticsSnapshotService snapshotService;
    @Mock
    private OrderStatisticsConfigService configService;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private MockedStatic<WebsiteContext> websiteContext;
    private OrderStatisticsSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsSubmissionService(
                statisticsService,
                snapshotService,
                configService,
                new ObjectMapper().findAndRegisterModules()
        );
        SystemUserDto loginUser = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .userType(SystemUserType.COMPANY_ADMIN)
                .build();
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
        saSessionUtil.when(SaSessionUtil::getViewMode).thenReturn(ViewMode.TEAM);
        websiteContext = mockStatic(WebsiteContext.class);
        websiteContext.when(WebsiteContext::isWebsiteAdmin).thenReturn(false);
        websiteContext.when(WebsiteContext::getCurrentWebsiteId).thenReturn(null);
        when(configService.getOrCreate(null)).thenReturn(OrderStatisticsUserConfig.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("USD", "1"))
                .build());
    }

    @AfterEach
    void tearDown() {
        websiteContext.close();
        saSessionUtil.close();
    }

    @Test
    void returnsCachedImmutableSnapshotWithoutQueryingAgain() {
        OrderStatisticsStoredSnapshot snapshot = new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "token-1",
                Instant.parse("2026-06-24T12:00:00Z"),
                Instant.parse("2026-06-24T12:30:00Z"),
                result()
        );
        when(snapshotService.findCachedResultToken(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(101L),
                anyString()
        )).thenReturn("token-1");
        when(snapshotService.get(9L, 101L, "token-1")).thenReturn(snapshot);

        OrderStatisticsQueryResponse response = service.submit(request(false));

        assertThat(response.getState()).isEqualTo("COMPLETED");
        assertThat(response.isCached()).isTrue();
        assertThat(response.getResultToken()).isEqualTo("token-1");
        verify(statisticsService, never()).query(
                org.mockito.ArgumentMatchers.any(OrderStatisticsQueryRequest.class)
        );
    }

    @Test
    void forceRefreshBypassesCacheAndStoresNewSnapshot() {
        when(statisticsService.query(
                org.mockito.ArgumentMatchers.any(OrderStatisticsQueryRequest.class)
        )).thenReturn(result());
        OrderStatisticsStoredSnapshot snapshot = new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "token-2",
                Instant.parse("2026-06-24T12:00:00Z"),
                Instant.parse("2026-06-24T12:30:00Z"),
                result()
        );
        when(snapshotService.store(9L, 101L, result())).thenReturn(snapshot);

        OrderStatisticsQueryResponse response = service.submit(request(true));

        assertThat(response.getResultToken()).isEqualTo("token-2");
        assertThat(response.isCached()).isFalse();
        verify(snapshotService, never()).findCachedResultToken(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                anyString()
        );
        verify(snapshotService).cacheResultToken(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(101L),
                anyString(),
                org.mockito.ArgumentMatchers.eq("token-2")
        );
    }

    @Test
    void redisFailureReturnsDegradedSynchronousResult() {
        when(snapshotService.findCachedResultToken(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(101L),
                anyString()
        )).thenThrow(new RedisConnectionFailureException("down"));
        when(statisticsService.query(
                org.mockito.ArgumentMatchers.any(OrderStatisticsQueryRequest.class)
        )).thenReturn(result());

        OrderStatisticsQueryResponse response = service.submit(request(false));

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getResultToken()).isNull();
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getMessage()).contains("Redis");
    }

    private OrderStatisticsQueryRequest request(boolean forceRefresh) {
        return OrderStatisticsQueryRequest.builder()
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2026-06-01"))
                .granularity(OrderStatisticsGranularity.DAY)
                .dimension(OrderStatisticsDimension.EMPLOYEE)
                .employeeIds(List.of("101"))
                .targetCurrencyCode("USD")
                .forceRefresh(forceRefresh)
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
}
