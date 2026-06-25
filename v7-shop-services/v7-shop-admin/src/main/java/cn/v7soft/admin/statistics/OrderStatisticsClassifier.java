package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class OrderStatisticsClassifier {

    private static final int RATE_SCALE = 16;

    public OrderStatisticsCategory classify(OrderStatus status) {
        Objects.requireNonNull(status, "订单状态不能为空");
        if (status == OrderStatus.INVALID) {
            return OrderStatisticsCategory.INVALID;
        }
        if (status == OrderStatus.DELIVERED) {
            return OrderStatisticsCategory.DELIVERED;
        }
        return OrderStatisticsCategory.UNDELIVERED;
    }

    public OrderStatisticsCounts summarize(
            long orderCount,
            long invalidOrderCount,
            long deliveredOrderCount
    ) {
        if (orderCount < 0 || invalidOrderCount < 0 || deliveredOrderCount < 0) {
            throw new IllegalArgumentException("订单数量不能为负数");
        }
        if (invalidOrderCount > orderCount) {
            throw new IllegalArgumentException("无效订单数不能超过订单总数");
        }
        long validOrderCount = orderCount - invalidOrderCount;
        if (deliveredOrderCount > validOrderCount) {
            throw new IllegalArgumentException("签收订单数不能超过有效订单数");
        }
        long undeliveredOrderCount = validOrderCount - deliveredOrderCount;
        BigDecimal deliveryRate = validOrderCount == 0
                ? null
                : BigDecimal.valueOf(deliveredOrderCount)
                .divide(BigDecimal.valueOf(validOrderCount), RATE_SCALE, RoundingMode.HALF_UP);
        return new OrderStatisticsCounts(
                orderCount,
                validOrderCount,
                invalidOrderCount,
                deliveredOrderCount,
                undeliveredOrderCount,
                deliveryRate
        );
    }
}
