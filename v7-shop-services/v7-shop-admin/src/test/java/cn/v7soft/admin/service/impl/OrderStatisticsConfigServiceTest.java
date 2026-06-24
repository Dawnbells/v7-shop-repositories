package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.SaveOrderStatisticsConfigRequest;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.OrderStatisticsUserConfigRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsConfigServiceTest {

    @Mock
    private OrderStatisticsUserConfigRepository repository;

    private OrderStatisticsConfigService service;
    private MockedStatic<SaSessionUtil> saSessionUtil;

    private final SystemUserDto loginUser = SystemUserDto.builder()
            .id("101")
            .companyId(9L)
            .name("Alice")
            .userType(SystemUserType.COMPANY_ADMIN)
            .build();

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsConfigService(repository);
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(loginUser);
    }

    @AfterEach
    void tearDown() {
        saSessionUtil.close();
    }

    @Test
    @DisplayName("getOrCreate: missing config uses USD and browser IANA timezone")
    void getOrCreateUsesUsdAndBrowserTimezone() {
        when(repository.findByCompanyIdAndOwnerId(9L, 101L)).thenReturn(Optional.empty());
        when(repository.save(any(OrderStatisticsUserConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderStatisticsUserConfig result = service.getOrCreate("America/Los_Angeles");

        assertThat(result.getCompanyId()).isEqualTo(9L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getDefaultTargetCurrencyCode()).isEqualTo("USD");
        assertThat(result.getTimeZoneId()).isEqualTo("America/Los_Angeles");
        assertThat(result.getExchangeRates()).containsExactlyEntriesOf(Map.of("USD", "1"));
        verify(repository).save(result);
    }

    @Test
    @DisplayName("getOrCreate: invalid browser timezone falls back to Asia/Shanghai")
    void getOrCreateInvalidTimezoneFallsBackToShanghai() {
        when(repository.findByCompanyIdAndOwnerId(9L, 101L)).thenReturn(Optional.empty());
        when(repository.save(any(OrderStatisticsUserConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderStatisticsUserConfig result = service.getOrCreate("Not/AZone");

        assertThat(result.getTimeZoneId()).isEqualTo("Asia/Shanghai");
    }

    @Test
    @DisplayName("save: normalizes currency codes and always forces USD rate to one")
    void saveNormalizesCurrenciesAndForcesUsd() {
        OrderStatisticsUserConfig existing = OrderStatisticsUserConfig.builder()
                .id(7L)
                .companyId(9L)
                .owner(loginUser.toOwner())
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("USD", "1"))
                .build();
        when(repository.findByCompanyIdAndOwnerId(9L, 101L)).thenReturn(Optional.of(existing));
        when(repository.save(any(OrderStatisticsUserConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LinkedHashMap<String, String> rates = new LinkedHashMap<>();
        rates.put("usd", "3");
        rates.put("cny", "7.20000000");
        SaveOrderStatisticsConfigRequest request = SaveOrderStatisticsConfigRequest.builder()
                .defaultTargetCurrencyCode("cny")
                .timeZoneId("America/Los_Angeles")
                .exchangeRates(rates)
                .build();

        OrderStatisticsUserConfig result = service.save(request);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getCompanyId()).isEqualTo(9L);
        assertThat(result.getOwner().getId()).isEqualTo(101L);
        assertThat(result.getDefaultTargetCurrencyCode()).isEqualTo("CNY");
        assertThat(result.getTimeZoneId()).isEqualTo("America/Los_Angeles");
        assertThat(result.getExchangeRates())
                .containsEntry("USD", "1")
                .containsEntry("CNY", "7.2");
    }

    @Test
    @DisplayName("save: rejects invalid IANA timezone")
    void saveRejectsInvalidTimezone() {
        SaveOrderStatisticsConfigRequest request = SaveOrderStatisticsConfigRequest.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Not/AZone")
                .exchangeRates(Map.of("USD", "1"))
                .build();

        assertThatThrownBy(() -> service.save(request))
                .hasMessageContaining("时区");
    }

    @Test
    @DisplayName("save: rejects non-positive, oversized, or over-precision rates")
    void saveRejectsInvalidRates() {
        assertInvalidRate("0");
        assertInvalidRate("-1");
        assertInvalidRate("1000000000.00000001");
        assertInvalidRate("7.123456789");
        assertInvalidRate("not-a-number");
    }

    private void assertInvalidRate(String rate) {
        SaveOrderStatisticsConfigRequest request = SaveOrderStatisticsConfigRequest.builder()
                .defaultTargetCurrencyCode("USD")
                .timeZoneId("Asia/Shanghai")
                .exchangeRates(Map.of("CNY", rate))
                .build();

        assertThatThrownBy(() -> service.save(request))
                .hasMessageContaining("汇率");
    }
}
