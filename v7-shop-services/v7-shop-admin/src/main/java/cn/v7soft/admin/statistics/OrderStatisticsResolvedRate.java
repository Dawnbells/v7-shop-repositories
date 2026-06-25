package cn.v7soft.admin.statistics;

import java.math.BigDecimal;

public record OrderStatisticsResolvedRate(
        String currencyCode,
        BigDecimal value,
        OrderStatisticsRateSource source
) {
}
