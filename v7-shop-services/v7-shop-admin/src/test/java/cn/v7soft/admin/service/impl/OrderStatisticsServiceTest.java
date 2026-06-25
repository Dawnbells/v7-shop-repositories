package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
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
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsServiceTest {

    @Mock
    private OrderStatisticsQueryRepository queryRepository;
    @Mock
    private OrderStatisticsConfigService configService;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private SystemUserRepository systemUserRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private MockedStatic<WebsiteContext> websiteContext;
    private OrderStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsService(
                queryRepository,
                configService,
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
                Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
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
    }

    @AfterEach
    void tearDown() {
        websiteContext.close();
        saSessionUtil.close();
    }

    @Test
    void queriesAggregateRowsAndReturnsConvertedResult() {
        OrderStatisticsUserConfig config = OrderStatisticsUserConfig.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("USD", "1"))
                .build();
        Currency usd = Currency.builder()
                .code("USD")
                .name("美元")
                .symbol("$")
                .exchangeRate(BigDecimal.ONE)
                .fractionDigits(2)
                .build();
        when(configService.getOrCreate(null)).thenReturn(config);
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

        OrderStatisticsQueryRequest request = OrderStatisticsQueryRequest.builder()
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2026-06-01"))
                .granularity(OrderStatisticsGranularity.DAY)
                .dimension(OrderStatisticsDimension.EMPLOYEE)
                .employeeIds(List.of("101"))
                .targetCurrencyCode("USD")
                .build();

        OrderStatisticsResultResponse result = service.query(request);

        assertThat(result.getSummary().getOrderCount()).isEqualTo(1);
        assertThat(result.getSummary().getDeliveredSalesAmount()).isEqualTo("25.00");
        assertThat(result.getTargetCurrencyCode()).isEqualTo("USD");
    }
}
