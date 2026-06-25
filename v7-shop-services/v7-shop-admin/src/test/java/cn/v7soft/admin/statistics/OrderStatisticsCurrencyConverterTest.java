package cn.v7soft.admin.statistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsCurrencyConverterTest {

    private final OrderStatisticsCurrencyConverter converter =
            new OrderStatisticsCurrencyConverter();

    @Test
    void convertsSourceCurrencyToUsd() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("720"),
                "CNY",
                "USD",
                Map.of(),
                Map.of(),
                new BigDecimal("7.2"),
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.converted()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("100");
        assertThat(result.sourceRate().source()).isEqualTo(OrderStatisticsRateSource.ORDER_HISTORY);
        assertThat(result.targetRate().source()).isEqualTo(OrderStatisticsRateSource.SYSTEM);
    }

    @Test
    void convertsUsdToTargetCurrency() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("100"),
                "USD",
                "CNY",
                Map.of(),
                Map.of(),
                null,
                Map.of("USD", BigDecimal.ONE, "CNY", new BigDecimal("7.2"))
        );

        assertThat(result.converted()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("720");
    }

    @Test
    void sameCurrencyDoesNotRequireAnyRate() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("100.25"),
                "EUR",
                "EUR",
                Map.of(),
                Map.of(),
                null,
                Map.of()
        );

        assertThat(result.converted()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("100.25");
        assertThat(result.sourceRate()).isNull();
        assertThat(result.targetRate()).isNull();
    }

    @Test
    void temporaryRateOverridesPersonalAndHistoricalRate() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("725"),
                "CNY",
                "USD",
                Map.of("CNY", new BigDecimal("7.25")),
                Map.of("CNY", new BigDecimal("7.2")),
                new BigDecimal("7.1"),
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.amount()).isEqualByComparingTo("100");
        assertThat(result.sourceRate().source()).isEqualTo(OrderStatisticsRateSource.TEMPORARY);
    }

    @Test
    void personalRateOverridesHistoricalRate() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("720"),
                "CNY",
                "USD",
                Map.of(),
                Map.of("CNY", new BigDecimal("7.2")),
                new BigDecimal("7.1"),
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.amount()).isEqualByComparingTo("100");
        assertThat(result.sourceRate().source()).isEqualTo(OrderStatisticsRateSource.PERSONAL);
    }

    @Test
    void reportsMissingSourceRateInsteadOfUsingOneToOne() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("100"),
                "EUR",
                "USD",
                Map.of(),
                Map.of(),
                null,
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.converted()).isFalse();
        assertThat(result.missingReason()).isEqualTo(
                OrderStatisticsMissingRateReason.SOURCE_RATE_MISSING
        );
    }

    @Test
    void reportsMissingTargetRateInsteadOfUsingOneToOne() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("100"),
                "USD",
                "EUR",
                Map.of(),
                Map.of(),
                null,
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.converted()).isFalse();
        assertThat(result.missingReason()).isEqualTo(
                OrderStatisticsMissingRateReason.TARGET_RATE_MISSING
        );
    }

    @Test
    void ignoresNonPositiveHistoricalRate() {
        OrderStatisticsConversionResult result = converter.convert(
                new BigDecimal("100"),
                "CNY",
                "USD",
                Map.of(),
                Map.of(),
                BigDecimal.ZERO,
                Map.of("USD", BigDecimal.ONE)
        );

        assertThat(result.converted()).isFalse();
        assertThat(result.missingReason()).isEqualTo(
                OrderStatisticsMissingRateReason.SOURCE_RATE_MISSING
        );
    }
}
