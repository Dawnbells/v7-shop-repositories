package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.resp.OrderStatisticsBucketResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsResultAssemblerTest {

    private final OrderStatisticsResultAssembler assembler =
            new OrderStatisticsResultAssembler(
                    new OrderStatisticsClassifier(),
                    new OrderStatisticsCurrencyConverter()
            );

    @Test
    void assemblesCountsConvertedSalesOriginalCurrenciesAndMissingRates() {
        List<OrderStatisticsBucket> buckets = List.of(
                bucket("2026-06-01"),
                bucket("2026-06-02")
        );
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-02"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L),
                List.of(),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
        List<OrderStatisticsAggregateRow> rows = List.of(
                row("2026-06-01", 101L, "Alice", "CNY", "7.2",
                        OrderStatus.DELIVERED, 2, "720"),
                row("2026-06-01", 101L, "Alice", "CNY", "7.2",
                        OrderStatus.INVALID, 1, "72"),
                row("2026-06-01", 101L, "Alice", "USD", "1",
                        OrderStatus.PENDING, 1, "50"),
                row("2026-06-01", 101L, "Alice", "EUR", null,
                        OrderStatus.PENDING, 1, "20")
        );

        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                rows,
                Map.of(),
                Map.of("USD", BigDecimal.ONE),
                Map.of(101L, "Alice"),
                2
        );

        assertThat(result.getSummary().getOrderCount()).isEqualTo(5);
        assertThat(result.getSummary().getValidOrderCount()).isEqualTo(4);
        assertThat(result.getSummary().getInvalidOrderCount()).isEqualTo(1);
        assertThat(result.getSummary().getDeliveredOrderCount()).isEqualTo(2);
        assertThat(result.getSummary().getUndeliveredOrderCount()).isEqualTo(2);
        assertThat(result.getSummary().getDeliveryRate()).isEqualTo("0.5000000000000000");
        assertThat(result.getSummary().getTotalSalesAmount()).isEqualTo("160.00");
        assertThat(result.getSummary().getInvalidSalesAmount()).isEqualTo("10.00");
        assertThat(result.getSummary().getUndeliveredSalesAmount()).isEqualTo("50.00");
        assertThat(result.getSummary().getDeliveredSalesAmount()).isEqualTo("100.00");
        assertThat(result.getSummary().getMissingRateOrderCount()).isEqualTo(1);

        assertThat(result.getBuckets()).hasSize(2);
        OrderStatisticsBucketResponse emptyBucket = result.getBuckets().get(1);
        assertThat(emptyBucket.getKey()).isEqualTo("2026-06-02");
        assertThat(emptyBucket.getMetrics().getOrderCount()).isZero();
        assertThat(emptyBucket.getMetrics().getDeliveryRate()).isNull();

        assertThat(result.getGroups()).singleElement().satisfies(group -> {
            assertThat(group.getGroupKey()).isEqualTo("EMPLOYEE:101");
            assertThat(group.getName()).isEqualTo("Alice");
            assertThat(group.getMetrics().getTotalSalesAmount()).isEqualTo("160.00");
        });

        assertThat(result.getOriginalCurrencies())
                .filteredOn(item -> item.getCurrencyCode().equals("CNY"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getOrderCount()).isEqualTo(3);
                    assertThat(item.getTotalAmount()).isEqualTo("792");
                });
        assertThat(result.getMissingRates()).singleElement().satisfies(item -> {
            assertThat(item.getCurrencyCode()).isEqualTo("EUR");
            assertThat(item.getOrderCount()).isEqualTo(1);
            assertThat(item.getOriginalAmount()).isEqualTo("20");
        });
    }

    @Test
    void assemblesBucketGroupsForEachBucketAndSelectedGroupWithZeroRows() {
        List<OrderStatisticsBucket> buckets = List.of(
                bucket("2026-06-01"),
                bucket("2026-06-02")
        );
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-02"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L),
                List.of(),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );

        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                List.of(row("2026-06-01", 101L, "Alice", "USD", "1",
                        OrderStatus.DELIVERED, 1, "25")),
                Map.of(),
                Map.of("USD", BigDecimal.ONE),
                Map.of(101L, "Alice"),
                2
        );

        assertThat(result.getBucketGroups()).hasSize(2);
        assertThat(result.getBucketGroups().get(0)).satisfies(item -> {
            assertThat(item.getBucketKey()).isEqualTo("2026-06-01");
            assertThat(item.getGroupKey()).isEqualTo("EMPLOYEE:101");
            assertThat(item.getName()).isEqualTo("Alice");
            assertThat(item.getMetrics().getOrderCount()).isEqualTo(1);
            assertThat(item.getMetrics().getDeliveredSalesAmount()).isEqualTo("25.00");
        });
        assertThat(result.getBucketGroups().get(1)).satisfies(item -> {
            assertThat(item.getBucketKey()).isEqualTo("2026-06-02");
            assertThat(item.getGroupKey()).isEqualTo("EMPLOYEE:101");
            assertThat(item.getName()).isEqualTo("Alice");
            assertThat(item.getMetrics().getOrderCount()).isZero();
            assertThat(item.getMetrics().getDeliveryRate()).isNull();
        });
    }
    private OrderStatisticsBucket bucket(String key) {
        LocalDate date = LocalDate.parse(key);
        return new OrderStatisticsBucket(
                key,
                Instant.parse(key + "T00:00:00Z"),
                Instant.parse(date.plusDays(1) + "T00:00:00Z"),
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(),
                false
        );
    }

    private OrderStatisticsAggregateRow row(
            String bucketKey,
            Long groupId,
            String groupName,
            String currencyCode,
            String historicalRate,
            OrderStatus status,
            long count,
            String amount
    ) {
        return new OrderStatisticsAggregateRow(
                bucketKey,
                groupId,
                groupName,
                currencyCode,
                historicalRate == null ? null : new BigDecimal(historicalRate),
                status,
                count,
                new BigDecimal(amount)
        );
    }
}
