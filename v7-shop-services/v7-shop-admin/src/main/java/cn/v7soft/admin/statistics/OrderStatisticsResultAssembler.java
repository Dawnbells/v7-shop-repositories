package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.resp.OrderStatisticsBucketResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMissingRateResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOriginalCurrencyResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderStatisticsResultAssembler {

    private static final String UNKNOWN_CURRENCY = "UNKNOWN";

    private final OrderStatisticsClassifier classifier;
    private final OrderStatisticsCurrencyConverter currencyConverter;

    public OrderStatisticsResultAssembler(
            OrderStatisticsClassifier classifier,
            OrderStatisticsCurrencyConverter currencyConverter
    ) {
        this.classifier = classifier;
        this.currencyConverter = currencyConverter;
    }

    public OrderStatisticsResultResponse assemble(
            List<OrderStatisticsBucket> buckets,
            OrderStatisticsQueryCriteria criteria,
            List<OrderStatisticsAggregateRow> rows,
            Map<String, String> personalRateStrings,
            Map<String, BigDecimal> systemRates,
            Map<Long, String> currentGroupNames,
            int targetFractionDigits,
            Instant generatedAt,
            ZoneId userZone
    ) {
        Map<String, BigDecimal> temporaryRates = parseRates(criteria.temporaryExchangeRates());
        Map<String, BigDecimal> personalRates = parseRates(personalRateStrings);
        MetricsAccumulator summary = new MetricsAccumulator();
        Map<String, MetricsAccumulator> bucketMetrics = new LinkedHashMap<>();
        Map<String, MetricsAccumulator> bucketGroupMetrics = new LinkedHashMap<>();
        Map<String, GroupAccumulator> groupMetrics = new LinkedHashMap<>();
        Map<String, OriginalCurrencyAccumulator> originalCurrencies = new LinkedHashMap<>();
        Map<String, MissingRateAccumulator> missingRates = new LinkedHashMap<>();

        for (OrderStatisticsBucket bucket : buckets) {
            bucketMetrics.put(bucket.key(), new MetricsAccumulator());
        }
        initializeSelectedGroups(criteria, currentGroupNames, groupMetrics);

        for (OrderStatisticsAggregateRow row : rows) {
            OrderStatisticsCategory category = classifier.classify(row.orderStatus());
            BigDecimal originalAmount = row.originalAmount() == null
                    ? BigDecimal.ZERO
                    : row.originalAmount();
            String currencyCode = normalizeCurrencyCode(row.currencyCode());
            OrderStatisticsConversionResult conversion = convert(
                    originalAmount,
                    currencyCode,
                    criteria.targetCurrencyCode(),
                    temporaryRates,
                    personalRates,
                    row.historicalExchangeRate(),
                    systemRates
            );

            summary.add(row.orderCount(), category, conversion);
            bucketMetrics.computeIfAbsent(row.bucketKey(), ignored -> new MetricsAccumulator())
                    .add(row.orderCount(), category, conversion);

            String groupKey = makeGroupKey(criteria, row.groupId());
            GroupAccumulator groupAccumulator = groupMetrics.computeIfAbsent(
                    groupKey,
                    ignored -> new GroupAccumulator(
                            groupKey,
                            row.groupId(),
                            resolveGroupName(criteria, row, currentGroupNames),
                            row.groupId() != null && !currentGroupNames.containsKey(row.groupId())
                    )
            );
            groupAccumulator.metrics.add(row.orderCount(), category, conversion);
            bucketGroupMetrics.computeIfAbsent(
                    makeBucketGroupKey(row.bucketKey(), groupKey),
                    ignored -> new MetricsAccumulator()
            ).add(row.orderCount(), category, conversion);

            originalCurrencies.computeIfAbsent(
                    currencyCode,
                    OriginalCurrencyAccumulator::new
            ).add(row.orderCount(), category, originalAmount);

            if (!conversion.converted()) {
                String missingKey = currencyCode + ":" + conversion.missingReason();
                missingRates.computeIfAbsent(
                        missingKey,
                        ignored -> new MissingRateAccumulator(
                                currencyCode,
                                conversion.missingReason()
                        )
                ).add(row.orderCount(), originalAmount);
            }
        }

        // 汇总（卡片）作为权威合计：全量高精度后统一舍入
        RoundedAmounts summaryRounded = roundEach(summary, targetFractionDigits);

        // 时间桶：余数分配使各桶合计精确等于汇总（趋势与卡片一致）
        List<MetricsAccumulator> bucketAccumulators = buckets.stream()
                .map(bucket -> bucketMetrics.get(bucket.key()))
                .toList();
        List<RoundedAmounts> bucketRounded =
                allocate(summary, bucketAccumulators, targetFractionDigits);
        List<OrderStatisticsBucketResponse> bucketResponses = new ArrayList<>();
        for (int index = 0; index < buckets.size(); index++) {
            OrderStatisticsBucket bucket = buckets.get(index);
            bucketResponses.add(OrderStatisticsBucketResponse.builder()
                    .key(bucket.key())
                    .startAt(bucket.startInstant().toString())
                    .endAt(bucket.endInstant().toString())
                    .partial(bucket.partial())
                    .metrics(bucketMetrics.get(bucket.key())
                            .toResponse(classifier, bucketRounded.get(index)))
                    .build());
        }

        List<GroupAccumulator> sortedGroups = groupMetrics.values().stream()
                .sorted(Comparator.comparing(
                        (GroupAccumulator group) -> group.metrics.totalAmount(),
                        Comparator.reverseOrder()
                ))
                .toList();

        // 分组：余数分配使各组合计精确等于汇总（§9.6 合计与卡片一致 / §20.2.1 汇总=分组合计）
        List<MetricsAccumulator> groupAccumulators = sortedGroups.stream()
                .map(group -> group.metrics)
                .toList();
        List<RoundedAmounts> groupRounded =
                allocate(summary, groupAccumulators, targetFractionDigits);
        List<OrderStatisticsGroupResponse> groupResponses = new ArrayList<>();
        for (int index = 0; index < sortedGroups.size(); index++) {
            groupResponses.add(sortedGroups.get(index)
                    .toResponse(classifier, groupRounded.get(index)));
        }

        // 时间×分组明细：作为二维明细各自舍入（不参与可加约束）
        List<OrderStatisticsBucketGroupResponse> bucketGroupResponses = new ArrayList<>();
        for (OrderStatisticsBucket bucket : buckets) {
            for (GroupAccumulator group : sortedGroups) {
                MetricsAccumulator metrics = bucketGroupMetrics.getOrDefault(
                        makeBucketGroupKey(bucket.key(), group.key),
                        new MetricsAccumulator()
                );
                bucketGroupResponses.add(OrderStatisticsBucketGroupResponse.builder()
                        .bucketKey(bucket.key())
                        .groupKey(group.key)
                        .id(group.id == null ? null : String.valueOf(group.id))
                        .name(group.name)
                        .historical(group.historical)
                        .metrics(metrics.toResponse(
                                classifier, roundEach(metrics, targetFractionDigits)))
                        .build());
            }
        }

        return OrderStatisticsResultResponse.builder()
                .generatedAt(generatedAt.toString())
                .timeZoneId(userZone.getId())
                .targetCurrencyCode(criteria.targetCurrencyCode())
                .summary(summary.toResponse(classifier, summaryRounded))
                .buckets(bucketResponses)
                .groups(groupResponses)
                .bucketGroups(bucketGroupResponses)
                .originalCurrencies(originalCurrencies.values().stream()
                        .map(OriginalCurrencyAccumulator::toResponse)
                        .toList())
                .missingRates(missingRates.values().stream()
                        .map(MissingRateAccumulator::toResponse)
                        .toList())
                .build();
    }

    private OrderStatisticsConversionResult convert(
            BigDecimal amount,
            String sourceCode,
            String targetCode,
            Map<String, BigDecimal> temporaryRates,
            Map<String, BigDecimal> personalRates,
            BigDecimal historicalRate,
            Map<String, BigDecimal> systemRates
    ) {
        if (UNKNOWN_CURRENCY.equals(sourceCode)) {
            return OrderStatisticsConversionResult.missing(
                    OrderStatisticsMissingRateReason.SOURCE_RATE_MISSING,
                    null,
                    null
            );
        }
        return currencyConverter.convert(
                amount,
                sourceCode,
                targetCode,
                temporaryRates,
                personalRates,
                historicalRate,
                systemRates
        );
    }

    private String makeGroupKey(
            OrderStatisticsQueryCriteria criteria,
            Long groupId
    ) {
        return criteria.dimension().name() + ":"
                + (groupId == null ? "UNASSIGNED" : groupId);
    }

    private void initializeSelectedGroups(
            OrderStatisticsQueryCriteria criteria,
            Map<Long, String> currentGroupNames,
            Map<String, GroupAccumulator> groupMetrics
    ) {
        // 仅按部门维度预置选中分组：部门 ID 已在 OrderStatisticsQueryNormalizer 经权限校验，
        // 零数据部门可安全显示（含名称）。员工 ID 在管理者模式未做范围二次校验，若为每个提交
        // 员工 ID 预置零数据分组并回显当前姓名，会被用来枚举权限范围外员工的姓名（§7.4）；
        // 故员工维度只从实际命中订单（已按数据权限裁剪）的数据行产生分组——管理者只能看到其
        // 授权范围内确有订单的员工。
        if ("DEPARTMENT".equals(criteria.dimension().name())) {
            for (Long groupId : selectedGroupIds(criteria)) {
                if (groupId == null) {
                    continue;
                }
                String groupKey = makeGroupKey(criteria, groupId);
                groupMetrics.computeIfAbsent(
                        groupKey,
                        ignored -> new GroupAccumulator(
                                groupKey,
                                groupId,
                                resolveSelectedGroupName(criteria, groupId, currentGroupNames),
                                !currentGroupNames.containsKey(groupId)
                        )
                );
            }
        }
        if (criteria.includeUnassigned()) {
            String groupKey = makeGroupKey(criteria, null);
            groupMetrics.computeIfAbsent(
                    groupKey,
                    ignored -> new GroupAccumulator(groupKey, null, "未归属", false)
            );
        }
    }

    private List<Long> selectedGroupIds(OrderStatisticsQueryCriteria criteria) {
        if ("EMPLOYEE".equals(criteria.dimension().name())) {
            return criteria.employeeIds() == null ? List.of() : criteria.employeeIds();
        }
        return criteria.departmentIds() == null ? List.of() : criteria.departmentIds();
    }

    private String resolveSelectedGroupName(
            OrderStatisticsQueryCriteria criteria,
            Long groupId,
            Map<Long, String> currentGroupNames
    ) {
        String currentName = currentGroupNames.get(groupId);
        if (currentName != null && !currentName.isBlank()) {
            return currentName;
        }
        return (criteria.dimension().name().equals("EMPLOYEE") ? "历史员工（" : "历史部门（")
                + groupId + "）";
    }

    private String makeBucketGroupKey(String bucketKey, String groupKey) {
        return bucketKey + "\u0000" + groupKey;
    }

    private String resolveGroupName(
            OrderStatisticsQueryCriteria criteria,
            OrderStatisticsAggregateRow row,
            Map<Long, String> currentGroupNames
    ) {
        if (row.groupId() == null) {
            return "未归属";
        }
        String currentName = currentGroupNames.get(row.groupId());
        if (currentName != null && !currentName.isBlank()) {
            return currentName;
        }
        if (row.groupNameSnapshot() != null && !row.groupNameSnapshot().isBlank()) {
            return row.groupNameSnapshot();
        }
        return (criteria.dimension().name().equals("EMPLOYEE") ? "历史员工（" : "历史部门（")
                + row.groupId() + "）";
    }

    private Map<String, BigDecimal> parseRates(Map<String, String> rates) {
        if (rates == null || rates.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, BigDecimal> result = new LinkedHashMap<>();
        rates.forEach((code, value) -> {
            if (code == null || value == null) {
                return;
            }
            try {
                result.put(code.toUpperCase(Locale.ROOT), new BigDecimal(value));
            } catch (NumberFormatException ignored) {
                // 跳过历史脏数据中的非法汇率，避免单条坏数据使整个查询 500；
                // 该币种因无有效个人/临时汇率，最终按其它优先级或"缺失汇率"处理（§8.3）。
            }
        });
        return result;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        return currencyCode == null || currencyCode.isBlank()
                ? UNKNOWN_CURRENCY
                : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private RoundedAmounts roundEach(MetricsAccumulator accumulator, int fractionDigits) {
        return new RoundedAmounts(
                accumulator.invalidAmount.setScale(fractionDigits, RoundingMode.HALF_UP),
                accumulator.undeliveredAmount.setScale(fractionDigits, RoundingMode.HALF_UP),
                accumulator.deliveredAmount.setScale(fractionDigits, RoundingMode.HALF_UP)
        );
    }

    /**
     * 余数分配（最大余数法）：把各单元三类销售额舍入，并按舍入余数微调，使其和精确等于
     * 汇总各类舍入值，从而分组合计、时间桶合计与汇总卡片完全一致（§9.6 / §20.2.1），
     * 避免页面与 Excel 出现尾数差异（§3.2）。汇总仍是全量高精度统一舍入的权威值，
     * 微调只在最小货币单位级别、按余数最公平地分摊。
     *
     * <p>前置条件：{@code units} 各高精度金额之和必须等于 {@code summary} 的对应高精度金额，
     * 即 units 是同一批订单按某一维度（分组键 / 时间桶）的完整且互斥划分——当前调用方
     * （按 groupKey、bucketKey 划分同一份聚合行）恒满足。若违反此前置条件，差额会被并入
     * 现有单元：合计仍等于卡片，但个别单元金额会失真。
     */
    private List<RoundedAmounts> allocate(
            MetricsAccumulator summary,
            List<MetricsAccumulator> units,
            int fractionDigits
    ) {
        List<BigDecimal> invalid = allocateComponent(
                summary.invalidAmount,
                units.stream().map(unit -> unit.invalidAmount).toList(),
                fractionDigits
        );
        List<BigDecimal> undelivered = allocateComponent(
                summary.undeliveredAmount,
                units.stream().map(unit -> unit.undeliveredAmount).toList(),
                fractionDigits
        );
        List<BigDecimal> delivered = allocateComponent(
                summary.deliveredAmount,
                units.stream().map(unit -> unit.deliveredAmount).toList(),
                fractionDigits
        );
        List<RoundedAmounts> result = new ArrayList<>(units.size());
        for (int index = 0; index < units.size(); index++) {
            result.add(new RoundedAmounts(
                    invalid.get(index),
                    undelivered.get(index),
                    delivered.get(index)
            ));
        }
        return result;
    }

    private List<BigDecimal> allocateComponent(
            BigDecimal total,
            List<BigDecimal> units,
            int fractionDigits
    ) {
        BigDecimal target = total.setScale(fractionDigits, RoundingMode.HALF_UP);
        List<BigDecimal> rounded = new ArrayList<>(units.size());
        for (BigDecimal unit : units) {
            rounded.add(unit.setScale(fractionDigits, RoundingMode.HALF_UP));
        }
        if (units.isEmpty()) {
            return rounded;
        }
        BigDecimal step = BigDecimal.ONE.movePointLeft(fractionDigits);
        BigDecimal sum = rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int steps = target.subtract(sum)
                .movePointRight(fractionDigits)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        if (steps == 0) {
            return rounded;
        }
        List<Integer> order = new ArrayList<>(units.size());
        for (int index = 0; index < units.size(); index++) {
            order.add(index);
        }
        // 舍入余数 = 高精度值 − 舍入值；steps>0 时优先补给“被向下舍入最多”的单元，反之扣减
        Comparator<Integer> byRemainder = Comparator.comparing(
                (Integer index) -> units.get(index).subtract(rounded.get(index))
        );
        order.sort(steps > 0 ? byRemainder.reversed() : byRemainder);
        int adjustments = Math.min(Math.abs(steps), order.size());
        for (int k = 0; k < adjustments; k++) {
            int index = order.get(k);
            rounded.set(index, steps > 0
                    ? rounded.get(index).add(step)
                    : rounded.get(index).subtract(step));
        }
        return rounded;
    }

    private record RoundedAmounts(
            BigDecimal invalid,
            BigDecimal undelivered,
            BigDecimal delivered
    ) {
        private BigDecimal total() {
            return invalid.add(undelivered).add(delivered);
        }
    }

    private static final class MetricsAccumulator {
        private long orderCount;
        private long invalidOrderCount;
        private long deliveredOrderCount;
        private long missingRateOrderCount;
        private BigDecimal invalidAmount = BigDecimal.ZERO;
        private BigDecimal undeliveredAmount = BigDecimal.ZERO;
        private BigDecimal deliveredAmount = BigDecimal.ZERO;

        private void add(
                long count,
                OrderStatisticsCategory category,
                OrderStatisticsConversionResult conversion
        ) {
            orderCount += count;
            if (category == OrderStatisticsCategory.INVALID) {
                invalidOrderCount += count;
            } else if (category == OrderStatisticsCategory.DELIVERED) {
                deliveredOrderCount += count;
            }
            if (!conversion.converted()) {
                missingRateOrderCount += count;
                return;
            }
            if (category == OrderStatisticsCategory.INVALID) {
                invalidAmount = invalidAmount.add(conversion.amount());
            } else if (category == OrderStatisticsCategory.DELIVERED) {
                deliveredAmount = deliveredAmount.add(conversion.amount());
            } else {
                undeliveredAmount = undeliveredAmount.add(conversion.amount());
            }
        }

        private BigDecimal totalAmount() {
            return invalidAmount.add(undeliveredAmount).add(deliveredAmount);
        }

        private OrderStatisticsMetricsResponse toResponse(
                OrderStatisticsClassifier classifier,
                RoundedAmounts amounts
        ) {
            OrderStatisticsCounts counts = classifier.summarize(
                    orderCount,
                    invalidOrderCount,
                    deliveredOrderCount
            );
            return OrderStatisticsMetricsResponse.builder()
                    .orderCount(counts.orderCount())
                    .validOrderCount(counts.validOrderCount())
                    .invalidOrderCount(counts.invalidOrderCount())
                    .deliveredOrderCount(counts.deliveredOrderCount())
                    .undeliveredOrderCount(counts.undeliveredOrderCount())
                    .deliveryRate(counts.deliveryRate() == null
                            ? null
                            : counts.deliveryRate().toPlainString())
                    .totalSalesAmount(amounts.total().toPlainString())
                    .invalidSalesAmount(amounts.invalid().toPlainString())
                    .undeliveredSalesAmount(amounts.undelivered().toPlainString())
                    .deliveredSalesAmount(amounts.delivered().toPlainString())
                    .missingRateOrderCount(missingRateOrderCount)
                    .build();
        }
    }

    private static final class GroupAccumulator {
        private final String key;
        private final Long id;
        private final String name;
        private final boolean historical;
        private final MetricsAccumulator metrics = new MetricsAccumulator();

        private GroupAccumulator(String key, Long id, String name, boolean historical) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.historical = historical;
        }

        private OrderStatisticsGroupResponse toResponse(
                OrderStatisticsClassifier classifier,
                RoundedAmounts amounts
        ) {
            return OrderStatisticsGroupResponse.builder()
                    .groupKey(key)
                    .id(id == null ? null : String.valueOf(id))
                    .name(name)
                    .historical(historical)
                    .metrics(metrics.toResponse(classifier, amounts))
                    .build();
        }
    }

    private static final class OriginalCurrencyAccumulator {
        private final String currencyCode;
        private long orderCount;
        private BigDecimal invalidAmount = BigDecimal.ZERO;
        private BigDecimal undeliveredAmount = BigDecimal.ZERO;
        private BigDecimal deliveredAmount = BigDecimal.ZERO;

        private OriginalCurrencyAccumulator(String currencyCode) {
            this.currencyCode = currencyCode;
        }

        private void add(long count, OrderStatisticsCategory category, BigDecimal amount) {
            orderCount += count;
            if (category == OrderStatisticsCategory.INVALID) {
                invalidAmount = invalidAmount.add(amount);
            } else if (category == OrderStatisticsCategory.DELIVERED) {
                deliveredAmount = deliveredAmount.add(amount);
            } else {
                undeliveredAmount = undeliveredAmount.add(amount);
            }
        }

        private OrderStatisticsOriginalCurrencyResponse toResponse() {
            return OrderStatisticsOriginalCurrencyResponse.builder()
                    .currencyCode(currencyCode)
                    .orderCount(orderCount)
                    .totalAmount(plain(invalidAmount.add(undeliveredAmount).add(deliveredAmount)))
                    .invalidAmount(plain(invalidAmount))
                    .undeliveredAmount(plain(undeliveredAmount))
                    .deliveredAmount(plain(deliveredAmount))
                    .build();
        }
    }

    private static final class MissingRateAccumulator {
        private final String currencyCode;
        private final OrderStatisticsMissingRateReason reason;
        private long orderCount;
        private BigDecimal originalAmount = BigDecimal.ZERO;

        private MissingRateAccumulator(
                String currencyCode,
                OrderStatisticsMissingRateReason reason
        ) {
            this.currencyCode = currencyCode;
            this.reason = reason;
        }

        private void add(long count, BigDecimal amount) {
            orderCount += count;
            originalAmount = originalAmount.add(amount);
        }

        private OrderStatisticsMissingRateResponse toResponse() {
            return OrderStatisticsMissingRateResponse.builder()
                    .currencyCode(currencyCode)
                    .reason(reason)
                    .orderCount(orderCount)
                    .originalAmount(plain(originalAmount))
                    .build();
        }
    }
}
