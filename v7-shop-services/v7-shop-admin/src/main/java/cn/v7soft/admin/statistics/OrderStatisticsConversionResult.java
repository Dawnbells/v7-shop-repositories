package cn.v7soft.admin.statistics;

import java.math.BigDecimal;

public record OrderStatisticsConversionResult(
        boolean converted,
        BigDecimal amount,
        OrderStatisticsResolvedRate sourceRate,
        OrderStatisticsResolvedRate targetRate,
        OrderStatisticsMissingRateReason missingReason
) {

    public static OrderStatisticsConversionResult success(
            BigDecimal amount,
            OrderStatisticsResolvedRate sourceRate,
            OrderStatisticsResolvedRate targetRate
    ) {
        return new OrderStatisticsConversionResult(true, amount, sourceRate, targetRate, null);
    }

    public static OrderStatisticsConversionResult missing(
            OrderStatisticsMissingRateReason reason,
            OrderStatisticsResolvedRate sourceRate,
            OrderStatisticsResolvedRate targetRate
    ) {
        return new OrderStatisticsConversionResult(false, null, sourceRate, targetRate, reason);
    }
}
