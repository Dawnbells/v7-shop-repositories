package cn.v7soft.admin.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class OrderStatisticsCurrencyConverter {

    private static final String USD = "USD";
    private static final int WORKING_SCALE = 16;

    /**
     * 约定转换：系统汇率(t_currencies.exchange_rate)与订单历史汇率
     * (t_order_context_infos.currency_exchange_rate)均以「1 个该币种 = N 美元」
     * （每单位的美元价值，如 EUR=1.06、IDR=0.0001）存储；而本换算器内部统一以
     * 「1 美元 = N 个该币种」（每美元可兑换的单位数，与个人/临时汇率一致）参与计算。
     * 因此系统汇率与历史汇率喂入换算器前必须取倒数。入参为 null 或非正时返回 null
     * （视为该币种无有效汇率，由调用方按"缺失汇率"处理）。
     */
    public static BigDecimal usdPerUnitToUnitsPerUsd(BigDecimal usdPerUnit) {
        if (usdPerUnit == null || usdPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return BigDecimal.ONE.divide(usdPerUnit, WORKING_SCALE, RoundingMode.HALF_UP);
    }

    public OrderStatisticsConversionResult convert(
            BigDecimal amount,
            String sourceCurrencyCode,
            String targetCurrencyCode,
            Map<String, BigDecimal> temporaryRates,
            Map<String, BigDecimal> personalRates,
            BigDecimal historicalSourceRate,
            Map<String, BigDecimal> systemRates
    ) {
        Objects.requireNonNull(amount, "订单金额不能为空");
        String sourceCode = normalizeCode(sourceCurrencyCode);
        String targetCode = normalizeCode(targetCurrencyCode);

        if (sourceCode.equals(targetCode)) {
            return OrderStatisticsConversionResult.success(amount, null, null);
        }

        OrderStatisticsResolvedRate sourceRate = resolveSourceRate(
                sourceCode,
                temporaryRates,
                personalRates,
                historicalSourceRate
        );
        if (sourceRate == null) {
            return OrderStatisticsConversionResult.missing(
                    OrderStatisticsMissingRateReason.SOURCE_RATE_MISSING,
                    null,
                    null
            );
        }

        OrderStatisticsResolvedRate targetRate = resolveTargetRate(
                targetCode,
                temporaryRates,
                personalRates,
                systemRates
        );
        if (targetRate == null) {
            return OrderStatisticsConversionResult.missing(
                    OrderStatisticsMissingRateReason.TARGET_RATE_MISSING,
                    sourceRate,
                    null
            );
        }

        BigDecimal convertedAmount = amount
                .divide(sourceRate.value(), WORKING_SCALE, RoundingMode.HALF_UP)
                .multiply(targetRate.value());
        return OrderStatisticsConversionResult.success(convertedAmount, sourceRate, targetRate);
    }

    private OrderStatisticsResolvedRate resolveSourceRate(
            String currencyCode,
            Map<String, BigDecimal> temporaryRates,
            Map<String, BigDecimal> personalRates,
            BigDecimal historicalRate
    ) {
        OrderStatisticsResolvedRate resolved = resolveMapRate(
                currencyCode,
                temporaryRates,
                OrderStatisticsRateSource.TEMPORARY
        );
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveMapRate(
                currencyCode,
                personalRates,
                OrderStatisticsRateSource.PERSONAL
        );
        if (resolved != null) {
            return resolved;
        }
        if (isPositive(historicalRate)) {
            return new OrderStatisticsResolvedRate(
                    currencyCode,
                    historicalRate,
                    OrderStatisticsRateSource.ORDER_HISTORY
            );
        }
        if (USD.equals(currencyCode)) {
            return new OrderStatisticsResolvedRate(
                    USD,
                    BigDecimal.ONE,
                    OrderStatisticsRateSource.SYSTEM
            );
        }
        return null;
    }

    private OrderStatisticsResolvedRate resolveTargetRate(
            String currencyCode,
            Map<String, BigDecimal> temporaryRates,
            Map<String, BigDecimal> personalRates,
            Map<String, BigDecimal> systemRates
    ) {
        OrderStatisticsResolvedRate resolved = resolveMapRate(
                currencyCode,
                temporaryRates,
                OrderStatisticsRateSource.TEMPORARY
        );
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveMapRate(
                currencyCode,
                personalRates,
                OrderStatisticsRateSource.PERSONAL
        );
        if (resolved != null) {
            return resolved;
        }
        resolved = resolveMapRate(
                currencyCode,
                systemRates,
                OrderStatisticsRateSource.SYSTEM
        );
        if (resolved != null) {
            return resolved;
        }
        if (USD.equals(currencyCode)) {
            return new OrderStatisticsResolvedRate(
                    USD,
                    BigDecimal.ONE,
                    OrderStatisticsRateSource.SYSTEM
            );
        }
        return null;
    }

    private OrderStatisticsResolvedRate resolveMapRate(
            String currencyCode,
            Map<String, BigDecimal> rates,
            OrderStatisticsRateSource source
    ) {
        if (rates == null || rates.isEmpty()) {
            return null;
        }
        BigDecimal rate = rates.get(currencyCode);
        if (rate == null) {
            rate = rates.entrySet().stream()
                    .filter(entry -> currencyCode.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return isPositive(rate)
                ? new OrderStatisticsResolvedRate(currencyCode, rate, source)
                : null;
    }

    private boolean isPositive(BigDecimal rate) {
        return rate != null && rate.compareTo(BigDecimal.ZERO) > 0;
    }

    private String normalizeCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("币种代码不能为空");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}
