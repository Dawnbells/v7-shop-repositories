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
import java.time.ZoneId;
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
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
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
        assertThat(result.getGeneratedAt()).isEqualTo("2026-06-10T12:00:00Z");
        assertThat(result.getTimeZoneId()).isEqualTo("Asia/Shanghai");
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
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
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
    @Test
    void groupAndBucketRoundedSumsEqualSummaryDespiteRoundingRemainder() {
        List<OrderStatisticsBucket> buckets = List.of(
                bucket("2026-06-01"),
                bucket("2026-06-02")
        );
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-02"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L, 102L),
                List.of(),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
        // 两笔各换算为 0.005 USD（CNY 0.05 ÷ 历史汇率 10），分属两组、两桶；
        // 汇总未签收 = 0.01，但各自独立四舍五入会得 0.01+0.01=0.02 —— 余数分配须修正回 0.01
        List<OrderStatisticsAggregateRow> rows = List.of(
                row("2026-06-01", 101L, "A", "CNY", "10",
                        OrderStatus.PENDING, 1, "0.05"),
                row("2026-06-02", 102L, "B", "CNY", "10",
                        OrderStatus.PENDING, 1, "0.05")
        );

        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                rows,
                Map.of(),
                Map.of("USD", BigDecimal.ONE),
                Map.of(101L, "A", 102L, "B"),
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(result.getSummary().getUndeliveredSalesAmount()).isEqualTo("0.01");
        assertThat(result.getSummary().getTotalSalesAmount()).isEqualTo("0.01");

        // §9.6 合计与卡片一致 / §20.2.1 汇总=分组合计
        assertThat(sumAmounts(result.getGroups().stream()
                .map(group -> group.getMetrics().getUndeliveredSalesAmount()).toList()))
                .isEqualByComparingTo("0.01");
        assertThat(sumAmounts(result.getGroups().stream()
                .map(group -> group.getMetrics().getTotalSalesAmount()).toList()))
                .isEqualByComparingTo("0.01");
        // 时间桶合计也等于汇总（趋势与卡片一致）
        assertThat(sumAmounts(result.getBuckets().stream()
                .map(bucket -> bucket.getMetrics().getUndeliveredSalesAmount()).toList()))
                .isEqualByComparingTo("0.01");
        assertThat(sumAmounts(result.getBuckets().stream()
                .map(bucket -> bucket.getMetrics().getTotalSalesAmount()).toList()))
                .isEqualByComparingTo("0.01");
    }

    private static BigDecimal sumAmounts(List<String> amounts) {
        return amounts.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void employeeModeOmitsSelectedEmployeesWithoutDataToPreventNameLeak() {
        List<OrderStatisticsBucket> buckets = List.of(bucket("2026-06-01"));
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L, 999L),
                List.of(),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
        // 只有 101 有订单；999 为权限范围外的伪造 ID（currentGroupNames 含其姓名以模拟泄露面）
        List<OrderStatisticsAggregateRow> rows = List.of(
                row("2026-06-01", 101L, "Alice", "USD", "1",
                        OrderStatus.DELIVERED, 1, "10"));

        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                rows,
                Map.of(),
                Map.of("USD", BigDecimal.ONE),
                Map.of(101L, "Alice", 999L, "范围外Bob"),
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        // 999 无命中订单 → 不预置零数据分组 → 不出现、绝不回显其姓名（§7.4）
        assertThat(result.getGroups())
                .extracting(group -> group.getId())
                .containsExactly("101");
    }

    @Test
    void departmentModeRetainsSelectedDepartmentsWithoutData() {
        List<OrderStatisticsBucket> buckets = List.of(bucket("2026-06-01"));
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.DEPARTMENT,
                List.of(),
                List.of(201L, 202L),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
        // 部门 ID 已经过权限校验，零数据部门 202 仍应预置显示（与员工维度的安全裁剪相区分）
        List<OrderStatisticsAggregateRow> rows = List.of(
                row("2026-06-01", 201L, "销售部", "USD", "1",
                        OrderStatus.DELIVERED, 1, "10"));

        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                rows,
                Map.of(),
                Map.of("USD", BigDecimal.ONE),
                Map.of(201L, "销售部", 202L, "市场部"),
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(result.getGroups())
                .extracting(group -> group.getId())
                .containsExactlyInAnyOrder("201", "202");
    }

    @Test
    void malformedPersonalRateIsSkippedInsteadOfFailingQuery() {
        List<OrderStatisticsBucket> buckets = List.of(bucket("2026-06-01"));
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
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
                row("2026-06-01", 101L, "Alice", "USD", "1",
                        OrderStatus.DELIVERED, 1, "10"));

        // 个人汇率含历史脏数据（非法值）时，跳过该条而非让整个查询抛错（§8.3）
        OrderStatisticsResultResponse result = assembler.assemble(
                buckets,
                criteria,
                rows,
                Map.of("CNY", "not-a-number", "EUR", "0.92"),
                Map.of("USD", BigDecimal.ONE),
                Map.of(101L, "Alice"),
                2,
                Instant.parse("2026-06-10T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(result.getSummary().getDeliveredSalesAmount()).isEqualTo("10.00");
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
