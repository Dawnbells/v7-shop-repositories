package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static cn.v7soft.admin.statistics.OrderStatisticsCategory.DELIVERED;
import static cn.v7soft.admin.statistics.OrderStatisticsCategory.INVALID;
import static cn.v7soft.admin.statistics.OrderStatisticsCategory.UNDELIVERED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatisticsClassifierTest {

    private final OrderStatisticsClassifier classifier = new OrderStatisticsClassifier();

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void classifiesEveryOrderStatusExactlyOnce(OrderStatus status) {
        OrderStatisticsCategory expected;
        if (status == OrderStatus.INVALID) {
            expected = INVALID;
        } else if (status == OrderStatus.DELIVERED) {
            expected = DELIVERED;
        } else {
            expected = UNDELIVERED;
        }

        assertThat(classifier.classify(status)).isEqualTo(expected);
    }

    @Test
    void summarizesValidInvalidDeliveredAndUndeliveredCounts() {
        OrderStatisticsCounts result = classifier.summarize(100, 8, 70);

        assertThat(result.orderCount()).isEqualTo(100);
        assertThat(result.invalidOrderCount()).isEqualTo(8);
        assertThat(result.validOrderCount()).isEqualTo(92);
        assertThat(result.deliveredOrderCount()).isEqualTo(70);
        assertThat(result.undeliveredOrderCount()).isEqualTo(22);
        assertThat(result.deliveryRate()).isEqualByComparingTo(
                new BigDecimal("0.7608695652173913")
        );
    }

    @Test
    void deliveryRateIsNullWhenThereAreNoValidOrders() {
        OrderStatisticsCounts result = classifier.summarize(3, 3, 0);

        assertThat(result.deliveryRate()).isNull();
    }

    @Test
    void rejectsImpossibleCountCombinations() {
        assertThatThrownBy(() -> classifier.summarize(5, 6, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> classifier.summarize(5, 1, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
