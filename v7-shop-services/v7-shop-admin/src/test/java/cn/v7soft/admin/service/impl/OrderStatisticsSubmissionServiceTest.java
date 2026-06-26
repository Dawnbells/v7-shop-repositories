package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsPageRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsPageResponse;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private CurrencyRepository currencyRepository;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private MockedStatic<WebsiteContext> websiteContext;
    private OrderStatisticsSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsSubmissionService(
                statisticsService,
                snapshotService,
                configService,
                new ObjectMapper().findAndRegisterModules(),
                currencyRepository
        );
        lenient().when(currencyRepository.findAllValid()).thenReturn(List.of());
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
        lenient().when(configService.getOrCreate(null)).thenReturn(OrderStatisticsUserConfig.builder()
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

    @Test
    void groupsPageReadsImmutableSnapshotAndSlicesList() {
        OrderStatisticsPageRequest pageRequest = new OrderStatisticsPageRequest();
        pageRequest.setPageNo(2);
        pageRequest.setPageSize(5);
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(new OrderStatisticsStoredSnapshot(
                        9L,
                        101L,
                        "token-1",
                        Instant.parse("2026-06-24T12:00:00Z"),
                        Instant.parse("2026-06-24T12:30:00Z"),
                        resultWithGroups(12)
                ));

        OrderStatisticsPageResponse<OrderStatisticsGroupResponse> page =
                service.groupsPage("token-1", pageRequest);

        assertThat(page.getTotal()).isEqualTo(12);
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getList())
                .extracting(OrderStatisticsGroupResponse::getName)
                .containsExactly("Group 6", "Group 7", "Group 8", "Group 9", "Group 10");
    }

    @Test
    void bucketGroupsPageReadsImmutableSnapshotAndSlicesList() {
        OrderStatisticsPageRequest pageRequest = new OrderStatisticsPageRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(5);
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(new OrderStatisticsStoredSnapshot(
                        9L,
                        101L,
                        "token-1",
                        Instant.parse("2026-06-24T12:00:00Z"),
                        Instant.parse("2026-06-24T12:30:00Z"),
                        resultWithBucketGroups(8)
                ));

        OrderStatisticsPageResponse<OrderStatisticsBucketGroupResponse> page =
                service.bucketGroupsPage("token-1", pageRequest);

        assertThat(page.getTotal()).isEqualTo(8);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getList())
                .extracting(OrderStatisticsBucketGroupResponse::getBucketKey)
                .containsExactly("2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05");
    }
    @Test
    void groupsPageSortsByOrderCountDescendingAcrossWholeSnapshot() {
        OrderStatisticsPageRequest pageRequest = new OrderStatisticsPageRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(5);
        pageRequest.setSortBy("orderCount desc");
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(new OrderStatisticsStoredSnapshot(
                        9L, 101L, "token-1",
                        Instant.parse("2026-06-24T12:00:00Z"),
                        Instant.parse("2026-06-24T12:30:00Z"),
                        resultWithGroups(12)));

        OrderStatisticsPageResponse<OrderStatisticsGroupResponse> page =
                service.groupsPage("token-1", pageRequest);

        // 全量按 orderCount 降序后首页：Group 12..8（而非快照原序 Group 1..5）
        assertThat(page.getList())
                .extracting(OrderStatisticsGroupResponse::getName)
                .containsExactly("Group 12", "Group 11", "Group 10", "Group 9", "Group 8");
    }

    @Test
    void groupsPageSortsBySalesAmountNumericallyNotLexicographically() {
        OrderStatisticsPageRequest pageRequest = new OrderStatisticsPageRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(5);
        pageRequest.setSortBy("totalSalesAmount asc");
        OrderStatisticsResultResponse result = OrderStatisticsResultResponse.builder()
                .targetCurrencyCode("USD")
                .summary(OrderStatisticsMetricsResponse.builder().orderCount(2).build())
                .buckets(List.of())
                .groups(List.of(
                        groupWithTotal("big", "1234.00"),
                        groupWithTotal("small", "9.00")))
                .bucketGroups(List.of())
                .originalCurrencies(List.of())
                .missingRates(List.of())
                .build();
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(new OrderStatisticsStoredSnapshot(
                        9L, 101L, "token-1",
                        Instant.parse("2026-06-24T12:00:00Z"),
                        Instant.parse("2026-06-24T12:30:00Z"),
                        result));

        OrderStatisticsPageResponse<OrderStatisticsGroupResponse> page =
                service.groupsPage("token-1", pageRequest);

        // 升序应为 9.00 < 1234.00（数值）；字典序会把 "1234.00" 排前
        assertThat(page.getList())
                .extracting(OrderStatisticsGroupResponse::getName)
                .containsExactly("small", "big");
    }

    private OrderStatisticsGroupResponse groupWithTotal(String name, String total) {
        return OrderStatisticsGroupResponse.builder()
                .groupKey("EMPLOYEE:" + name)
                .id(name)
                .name(name)
                .metrics(OrderStatisticsMetricsResponse.builder()
                        .totalSalesAmount(total)
                        .build())
                .build();
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

    private OrderStatisticsResultResponse resultWithGroups(int count) {
        return OrderStatisticsResultResponse.builder()
                .targetCurrencyCode("USD")
                .summary(OrderStatisticsMetricsResponse.builder().orderCount(count).build())
                .buckets(List.of())
                .groups(IntStream.rangeClosed(1, count)
                        .mapToObj(index -> OrderStatisticsGroupResponse.builder()
                                .groupKey("EMPLOYEE:" + index)
                                .id(String.valueOf(index))
                                .name("Group " + index)
                                .metrics(OrderStatisticsMetricsResponse.builder()
                                        .orderCount(index)
                                        .build())
                                .build())
                        .toList())
                .bucketGroups(List.of())
                .originalCurrencies(List.of())
                .missingRates(List.of())
                .build();
    }

    private OrderStatisticsResultResponse resultWithBucketGroups(int count) {
        return OrderStatisticsResultResponse.builder()
                .targetCurrencyCode("USD")
                .summary(OrderStatisticsMetricsResponse.builder().orderCount(count).build())
                .buckets(List.of())
                .groups(List.of())
                .bucketGroups(IntStream.rangeClosed(1, count)
                        .mapToObj(index -> OrderStatisticsBucketGroupResponse.builder()
                                .bucketKey("2026-06-" + String.format("%02d", index))
                                .groupKey("EMPLOYEE:101")
                                .id("101")
                                .name("Alice")
                                .metrics(OrderStatisticsMetricsResponse.builder()
                                        .orderCount(index)
                                        .build())
                                .build())
                        .toList())
                .originalCurrencies(List.of())
                .missingRates(List.of())
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
