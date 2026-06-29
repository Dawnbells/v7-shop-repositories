package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.statistics.FxRateService;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScope;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScopeResolver;
import cn.v7soft.admin.statistics.OrderStatisticsAggregateRow;
import cn.v7soft.admin.statistics.OrderStatisticsBucketFactory;
import cn.v7soft.admin.statistics.OrderStatisticsClassifier;
import cn.v7soft.admin.statistics.OrderStatisticsCurrencyConverter;
import cn.v7soft.admin.statistics.OrderStatisticsQueryNormalizer;
import cn.v7soft.admin.statistics.OrderStatisticsQueryRepository;
import cn.v7soft.admin.statistics.OrderStatisticsResultAssembler;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsExecutionServiceTest {

    @Mock
    private OrderStatisticsQueryRepository queryRepository;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private SystemUserRepository systemUserRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private OrderStatisticsExecutionService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsExecutionService(
                queryRepository,
                currencyRepository,
                systemUserRepository,
                departmentRepository,
                new OrderStatisticsBucketFactory(),
                new OrderStatisticsAccessScopeResolver(),
                new OrderStatisticsQueryNormalizer(),
                new OrderStatisticsResultAssembler(
                        new OrderStatisticsClassifier(),
                        new OrderStatisticsCurrencyConverter()
                ),
                Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC),
                new FxRateService(null) {
                    @Override
                    public Map<String, BigDecimal> latestUnitsPerUsd() {
                        return Map.of("USD", BigDecimal.ONE);
                    }
                }
        );
    }

    @Test
    void executesWithCapturedWebsiteAndUserContext() {
        Currency usd = Currency.builder()
                .code("USD")
                .name("美元")
                .exchangeRate(BigDecimal.ONE)
                .fractionDigits(2)
                .build();
        when(currencyRepository.findAllValid()).thenReturn(List.of(usd));
        when(systemUserRepository.findAllById(any())).thenReturn(List.of());
        when(queryRepository.query(any(), any(), any())).thenReturn(List.of(
                new OrderStatisticsAggregateRow(
                        "2026-06-01",
                        101L,
                        "Alice",
                        "USD",
                        BigDecimal.ONE,
                        OrderStatus.DELIVERED,
                        1,
                        new BigDecimal("25")
                )
        ));
        SystemUserDto user = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .userType(SystemUserType.COMPANY_ADMIN)
                .build();
        OrderStatisticsUserConfig config = OrderStatisticsUserConfig.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("USD", "1"))
                .build();
        OrderStatisticsExecutionContext context = new OrderStatisticsExecutionContext(
                user,
                ViewMode.TEAM,
                true,
                77L,
                config
        );

        OrderStatisticsResultResponse result = service.execute(request(), context);

        assertThat(result.getSummary().getDeliveredSalesAmount()).isEqualTo("25.00");
        assertThat(result.getGeneratedAt()).isEqualTo("2026-06-10T12:00:00Z");
        assertThat(result.getTimeZoneId()).isEqualTo("Asia/Shanghai");
        ArgumentCaptor<OrderStatisticsAccessScope> scope =
                ArgumentCaptor.forClass(OrderStatisticsAccessScope.class);
        org.mockito.Mockito.verify(queryRepository).query(any(), any(), scope.capture());
        assertThat(scope.getValue().websiteScoped()).isTrue();
        assertThat(scope.getValue().websiteId()).isEqualTo(77L);
        assertThat(scope.getValue().requesterUserId()).isEqualTo(101L);
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
}
