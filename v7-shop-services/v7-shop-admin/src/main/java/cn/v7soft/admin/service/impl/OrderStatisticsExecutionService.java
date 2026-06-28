package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScope;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScopeResolver;
import cn.v7soft.admin.statistics.OrderStatisticsAggregateRow;
import cn.v7soft.admin.statistics.OrderStatisticsBucket;
import cn.v7soft.admin.statistics.OrderStatisticsBucketFactory;
import cn.v7soft.admin.statistics.OrderStatisticsClassifier;
import cn.v7soft.admin.statistics.OrderStatisticsCurrencyConverter;
import cn.v7soft.admin.statistics.OrderStatisticsQueryCriteria;
import cn.v7soft.admin.statistics.OrderStatisticsQueryNormalizer;
import cn.v7soft.admin.statistics.OrderStatisticsQueryRepository;
import cn.v7soft.admin.statistics.OrderStatisticsResultAssembler;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class OrderStatisticsExecutionService {

    private final OrderStatisticsQueryRepository queryRepository;
    private final CurrencyRepository currencyRepository;
    private final SystemUserRepository systemUserRepository;
    private final DepartmentRepository departmentRepository;
    private final OrderStatisticsBucketFactory bucketFactory;
    private final OrderStatisticsAccessScopeResolver scopeResolver;
    private final OrderStatisticsQueryNormalizer queryNormalizer;
    private final OrderStatisticsResultAssembler resultAssembler;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderStatisticsExecutionService(
            OrderStatisticsQueryRepository queryRepository,
            CurrencyRepository currencyRepository,
            SystemUserRepository systemUserRepository,
            DepartmentRepository departmentRepository
    ) {
        this(
                queryRepository,
                currencyRepository,
                systemUserRepository,
                departmentRepository,
                new OrderStatisticsBucketFactory(),
                new OrderStatisticsAccessScopeResolver(),
                new OrderStatisticsQueryNormalizer(),
                new OrderStatisticsResultAssembler(
                        new OrderStatisticsClassifier(),
                        new OrderStatisticsCurrencyConverter()
                ),
                Clock.systemUTC()
        );
    }

    OrderStatisticsExecutionService(
            OrderStatisticsQueryRepository queryRepository,
            CurrencyRepository currencyRepository,
            SystemUserRepository systemUserRepository,
            DepartmentRepository departmentRepository,
            OrderStatisticsBucketFactory bucketFactory,
            OrderStatisticsAccessScopeResolver scopeResolver,
            OrderStatisticsQueryNormalizer queryNormalizer,
            OrderStatisticsResultAssembler resultAssembler,
            Clock clock
    ) {
        this.queryRepository = queryRepository;
        this.currencyRepository = currencyRepository;
        this.systemUserRepository = systemUserRepository;
        this.departmentRepository = departmentRepository;
        this.bucketFactory = bucketFactory;
        this.scopeResolver = scopeResolver;
        this.queryNormalizer = queryNormalizer;
        this.resultAssembler = resultAssembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderStatisticsResultResponse execute(
            OrderStatisticsQueryRequest request,
            OrderStatisticsExecutionContext executionContext
    ) {
        OrderStatisticsAccessScope scope = scopeResolver.resolve(
                executionContext.user(),
                executionContext.viewMode(),
                executionContext.websiteScoped(),
                executionContext.websiteId()
        );
        OrderStatisticsQueryCriteria criteria = queryNormalizer.normalize(request, scope);
        ZoneId userZone = ZoneId.of(executionContext.config().getTimeZoneId());
        Instant now = clock.instant();
        List<OrderStatisticsBucket> buckets = bucketFactory.create(
                criteria.startDate(),
                criteria.endDate(),
                criteria.granularity(),
                userZone,
                now
        );

        List<Currency> currencies = currencyRepository.findAllValid();
        Currency targetCurrency = currencies.stream()
                .filter(currency -> criteria.targetCurrencyCode()
                        .equalsIgnoreCase(currency.getCode()))
                .findFirst()
                .orElseGet(() -> syntheticUsd(criteria.targetCurrencyCode()));
        Map<String, BigDecimal> systemRates = new LinkedHashMap<>();
        systemRates.put("USD", BigDecimal.ONE);
        currencies.forEach(currency -> {
            if (currency.getExchangeRate() != null) {
                systemRates.put(
                        currency.getCode().toUpperCase(Locale.ROOT),
                        currency.getExchangeRate()
                );
            }
        });

        List<OrderStatisticsAggregateRow> rows =
                queryRepository.query(buckets, criteria, scope);
        return resultAssembler.assemble(
                buckets,
                criteria,
                rows,
                executionContext.config().getExchangeRates(),
                systemRates,
                loadCurrentGroupNames(criteria, rows),
                targetCurrency.getFractionDigits(),
                now,
                userZone
        );
    }

    private Map<Long, String> loadCurrentGroupNames(
            OrderStatisticsQueryCriteria criteria,
            List<OrderStatisticsAggregateRow> rows
    ) {
        LinkedHashMap<Long, String> result = new LinkedHashMap<>();
        if (criteria.dimension() == OrderStatisticsDimension.EMPLOYEE) {
            // 仅解析实际命中订单（已按数据权限裁剪）的员工，避免把权限范围外伪造 ID 的姓名
            // 读入内存（§7.4 防御纵深）；员工分组本就只从命中行产生，无需提交 ID 的名称。
            List<Long> hitEmployeeIds = rows.stream()
                    .map(OrderStatisticsAggregateRow::groupId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            systemUserRepository.findAllById(hitEmployeeIds)
                    .forEach(user -> result.put(user.getId(), user.getName()));
        } else {
            // 部门 ID 已在 normalizer 经权限校验，可安全解析（含零数据部门，用于预置显示）；
            // 全部模式下 departmentIds 为空，故并入「实际命中行」的部门 ID（同样已按数据权限裁剪），
            // 使现存部门显示当前名、不被误标为历史，孤儿部门（已删除）仍回落快照名并标历史。
            LinkedHashSet<Long> departmentIds = new LinkedHashSet<>(criteria.departmentIds());
            rows.stream()
                    .map(OrderStatisticsAggregateRow::groupId)
                    .filter(Objects::nonNull)
                    .forEach(departmentIds::add);
            departmentRepository.findAllById(departmentIds)
                    .forEach(department ->
                            result.put(department.getId(), department.getName()));
        }
        return result;
    }

    private Currency syntheticUsd(String targetCurrencyCode) {
        if (!"USD".equalsIgnoreCase(targetCurrencyCode)) {
            throw new IllegalArgumentException("目标币种不存在或已停用");
        }
        return Currency.builder()
                .code("USD")
                .name("美元")
                .symbol("$")
                .exchangeRate(BigDecimal.ONE)
                .fractionDigits(2)
                .build();
    }
}
