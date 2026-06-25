package cn.v7soft.admin.statistics;

import java.math.BigDecimal;

public record OrderStatisticsCounts(
        long orderCount,
        long validOrderCount,
        long invalidOrderCount,
        long deliveredOrderCount,
        long undeliveredOrderCount,
        BigDecimal deliveryRate
) {
}
