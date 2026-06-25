package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatus;

import java.math.BigDecimal;

public record OrderStatisticsAggregateRow(
        String bucketKey,
        Long groupId,
        String groupNameSnapshot,
        String currencyCode,
        BigDecimal historicalExchangeRate,
        OrderStatus orderStatus,
        long orderCount,
        BigDecimal originalAmount
) {
}
