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
            int targetFractionDigits
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

        List<OrderStatisticsBucketResponse> bucketResponses = buckets.stream()
                .map(bucket -> OrderStatisticsBucketResponse.builder()
                        .key(bucket.key())
                        .startAt(bucket.startInstant().toString())
                        .endAt(bucket.endInstant().toString())
                        .partial(bucket.partial())
                        .metrics(bucketMetrics.get(bucket.key())
                                .toResponse(classifier, targetFractionDigits))
                        .build())
                .toList();

        List<GroupAccumulator> sortedGroups = groupMetrics.values().stream()
                .sorted(Comparator.comparing(
                        (GroupAccumulator group) -> group.metrics.totalAmount(),
                        Comparator.reverseOrder()
                ))
                .toList();

        List<OrderStatisticsGroupResponse> groupResponses = sortedGroups.stream()
                .map(group -> group.toResponse(classifier, targetFractionDigits))
                .toList();

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
                        .metrics(metrics.toResponse(classifier, targetFractionDigits))
                        .build());
            }
        }

        return OrderStatisticsResultResponse.builder()
                .targetCurrencyCode(criteria.targetCurrencyCode())
                .summary(summary.toResponse(classifier, targetFractionDigits))
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
        rates.forEach((code, value) -> result.put(
                code.toUpperCase(Locale.ROOT),
                new BigDecimal(value)
        ));
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
                int fractionDigits
        ) {
            OrderStatisticsCounts counts = classifier.summarize(
                    orderCount,
                    invalidOrderCount,
                    deliveredOrderCount
            );
            BigDecimal roundedInvalid = invalidAmount.setScale(fractionDigits, RoundingMode.HALF_UP);
            BigDecimal roundedUndelivered = undeliveredAmount.setScale(fractionDigits, RoundingMode.HALF_UP);
            BigDecimal roundedDelivered = deliveredAmount.setScale(fractionDigits, RoundingMode.HALF_UP);
            BigDecimal roundedTotal = roundedInvalid.add(roundedUndelivered).add(roundedDelivered);
            return OrderStatisticsMetricsResponse.builder()
                    .orderCount(counts.orderCount())
                    .validOrderCount(counts.validOrderCount())
                    .invalidOrderCount(counts.invalidOrderCount())
                    .deliveredOrderCount(counts.deliveredOrderCount())
                    .undeliveredOrderCount(counts.undeliveredOrderCount())
                    .deliveryRate(counts.deliveryRate() == null
                            ? null
                            : counts.deliveryRate().toPlainString())
                    .totalSalesAmount(roundedTotal.toPlainString())
                    .invalidSalesAmount(roundedInvalid.toPlainString())
                    .undeliveredSalesAmount(roundedUndelivered.toPlainString())
                    .deliveredSalesAmount(roundedDelivered.toPlainString())
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
                int fractionDigits
        ) {
            return OrderStatisticsGroupResponse.builder()
                    .groupKey(key)
                    .id(id == null ? null : String.valueOf(id))
                    .name(name)
                    .historical(historical)
                    .metrics(metrics.toResponse(classifier, fractionDigits))
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
