package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsContextResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsCurrencyOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOptionResponse;
import cn.v7soft.admin.statistics.OrderStatisticsOptionRepository;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsOptionServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private OrderStatisticsOptionRepository optionRepository;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private MockedStatic<WebsiteContext> websiteContext;
    private OrderStatisticsOptionService service;
    private SystemUserDto loginUser;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsOptionService(
                currencyRepository,
                optionRepository,
                new cn.v7soft.admin.statistics.FxRateService(null) {
                    @Override
                    public java.util.Map<String, BigDecimal> latestUnitsPerUsd() {
                        // 仅含 USD：让测试里的 CNY 走「系统汇率取倒数」回退路径（断言 8）
                        return java.util.Map.of("USD", BigDecimal.ONE);
                    }
                });
        loginUser = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .userType(SystemUserType.COMPANY_ADMIN)
                .build();
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenAnswer(ignored -> loginUser);
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
    void employeeContextLocksDimensionToRequester() {
        loginUser.setUserType(SystemUserType.EMPLOYEE);

        OrderStatisticsContextResponse context = service.context();

        assertThat(context.isEmployeeLocked()).isTrue();
        assertThat(context.getDimensions()).containsExactly(OrderStatisticsDimension.EMPLOYEE);
        assertThat(context.isAllowUnassigned()).isFalse();
        assertThat(context.getRequesterUserId()).isEqualTo("101");
    }

    @Test
    void companyAdminContextAllowsBothDimensionsAndUnassigned() {
        OrderStatisticsContextResponse context = service.context();

        assertThat(context.getDimensions())
                .containsExactly(
                        OrderStatisticsDimension.EMPLOYEE,
                        OrderStatisticsDimension.DEPARTMENT
                );
        assertThat(context.isAllowUnassigned()).isTrue();
    }

    @Test
    void currenciesAlwaysContainUsdAndAreSorted() {
        // t_currencies 存「1 币种 = N 美元」：CNY 0.125（1 CNY=0.125 USD）
        when(currencyRepository.findAllValid()).thenReturn(List.of(
                Currency.builder()
                        .code("CNY")
                        .name("人民币")
                        .symbol("¥")
                        .exchangeRate(new BigDecimal("0.125"))
                        .fractionDigits(2)
                        .build()
        ));

        List<OrderStatisticsCurrencyOptionResponse> result = service.currencies();

        assertThat(result).extracting(OrderStatisticsCurrencyOptionResponse::getCode)
                .containsExactly("CNY", "USD");
        // 下发口径统一为「1 美元 = N 币种」(units-per-usd)：0.125 取倒数为 8，USD 恒为 1
        assertThat(result.get(0).getExchangeRate()).isEqualTo("8");
        assertThat(result.get(1).getExchangeRate()).isEqualTo("1");
    }

    @Test
    void employeeOptionsDelegateWithResolvedScope() {
        when(optionRepository.employees(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("ali"),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(List.of(
                OrderStatisticsOptionResponse.builder()
                        .id("101")
                        .name("Alice")
                        .historical(false)
                        .build()
        ));

        List<OrderStatisticsOptionResponse> result = service.employees("ali", true);

        assertThat(result).singleElement()
                .extracting(OrderStatisticsOptionResponse::getName)
                .isEqualTo("Alice");
    }

    @Test
    void domainOptionsAreNormalizedAndDeduplicated() {
        when(optionRepository.domains(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("shop")
        )).thenReturn(List.of("A.SHOP.COM", "a.shop.com", "b.shop.com"));

        List<String> result = service.domains("shop");

        assertThat(result).containsExactly("a.shop.com", "b.shop.com");
    }
}
