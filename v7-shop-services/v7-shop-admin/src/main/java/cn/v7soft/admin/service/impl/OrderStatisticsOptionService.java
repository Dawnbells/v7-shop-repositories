package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsContextResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsCurrencyOptionResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsOptionResponse;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScope;
import cn.v7soft.admin.statistics.OrderStatisticsAccessScopeResolver;
import cn.v7soft.admin.statistics.OrderStatisticsOptionRepository;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Service
public class OrderStatisticsOptionService {

    private static final String USD = "USD";
    private final CurrencyRepository currencyRepository;
    private final OrderStatisticsOptionRepository optionRepository;
    private final cn.v7soft.admin.statistics.FxRateService fxRateService;
    private final OrderStatisticsAccessScopeResolver scopeResolver;

    public OrderStatisticsOptionService(
            CurrencyRepository currencyRepository,
            OrderStatisticsOptionRepository optionRepository,
            cn.v7soft.admin.statistics.FxRateService fxRateService
    ) {
        this.currencyRepository = currencyRepository;
        this.optionRepository = optionRepository;
        this.fxRateService = fxRateService;
        this.scopeResolver = new OrderStatisticsAccessScopeResolver();
    }

    public OrderStatisticsContextResponse context() {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        OrderStatisticsAccessScope scope = scope();
        boolean employeeLocked = scope.personalOnly();
        List<OrderStatisticsDimension> dimensions = employeeLocked
                ? List.of(OrderStatisticsDimension.EMPLOYEE)
                : List.of(
                        OrderStatisticsDimension.EMPLOYEE,
                        OrderStatisticsDimension.DEPARTMENT
                );
        return OrderStatisticsContextResponse.builder()
                .requesterUserId(user.getId())
                .requesterName(user.getName())
                .dimensions(dimensions)
                .employeeLocked(employeeLocked)
                .allowUnassigned(scope.allowUnassigned())
                .websiteScoped(scope.websiteScoped())
                .websiteId(scope.websiteId() == null ? null : String.valueOf(scope.websiteId()))
                .platforms(List.of(WebsiteTypeEnum.values()))
                .dayRangeMaxMonths(2)
                .monthRangeMaxYears(5)
                .build();
    }

    @Transactional(readOnly = true)
    public List<OrderStatisticsCurrencyOptionResponse> currencies() {
        Map<String, Currency> currencies = new LinkedHashMap<>();
        for (Currency currency : currencyRepository.findAllValid()) {
            if (currency.getCode() == null) {
                continue;
            }
            currencies.put(currency.getCode().trim().toUpperCase(Locale.ROOT), currency);
        }
        currencies.putIfAbsent(USD, Currency.builder()
                .code(USD)
                .name("美元")
                .symbol("$")
                .exchangeRate(BigDecimal.ONE)
                .fractionDigits(2)
                .build());
        Currency usd = currencies.get(USD);
        usd.setExchangeRate(BigDecimal.ONE);
        Map<String, BigDecimal> realFxRates = fxRateService.latestUnitsPerUsd();
        return currencies.values().stream()
                .map(currency -> toCurrencyOption(currency, realFxRates))
                .sorted(Comparator.comparing(OrderStatisticsCurrencyOptionResponse::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderStatisticsOptionResponse> employees(
            String keyword,
            boolean includeHistorical
    ) {
        OrderStatisticsAccessScope scope = scope();
        if (scope.personalOnly()) {
            SystemUserDto user = SaSessionUtil.getLoginUser();
            return List.of(OrderStatisticsOptionResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .departmentId(user.getDepartmentId() == null
                            ? null
                            : String.valueOf(user.getDepartmentId()))
                    .departmentName(user.getDepartmentName())
                    .telephone(user.getTelephone())
                    .historical(false)
                    .disabled(false)
                    .build());
        }
        return optionRepository.employees(scope, normalizeKeyword(keyword), includeHistorical);
    }

    @Transactional(readOnly = true)
    public List<OrderStatisticsOptionResponse> departments(
            String keyword,
            boolean includeHistorical
    ) {
        OrderStatisticsAccessScope scope = scope();
        if (scope.personalOnly()) {
            return List.of();
        }
        return optionRepository.departments(scope, normalizeKeyword(keyword), includeHistorical);
    }

    @Transactional(readOnly = true)
    public List<String> domains(String keyword) {
        List<String> candidates = optionRepository.domains(scope(), normalizeKeyword(keyword));
        TreeSet<String> result = new TreeSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                result.add(candidate.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(result);
    }

    private OrderStatisticsCurrencyOptionResponse toCurrencyOption(
            Currency currency,
            Map<String, BigDecimal> realFxRates
    ) {
        // 统计页/个人中心的汇率口径统一为「1 美元 = N 个该币种」(units-per-usd)。
        // 参考汇率优先用统计真实汇率（与实际换算口径一致，未配置币种默认即用此值）；
        // 真实汇率缺该币种时，回退 t_currencies.exchange_rate（存「1 币种 = N 美元」）取倒数。
        String code = currency.getCode().trim().toUpperCase(Locale.ROOT);
        BigDecimal unitsPerUsd = realFxRates.get(code);
        if (unitsPerUsd == null) {
            unitsPerUsd = cn.v7soft.admin.statistics.OrderStatisticsCurrencyConverter
                    .usdPerUnitToUnitsPerUsd(currency.getExchangeRate());
        }
        return OrderStatisticsCurrencyOptionResponse.builder()
                .code(code)
                .name(currency.getName())
                .symbol(currency.getSymbol())
                .exchangeRate(unitsPerUsd == null
                        ? null
                        : unitsPerUsd.stripTrailingZeros().toPlainString())
                .fractionDigits(currency.getFractionDigits())
                .build();
    }

    private OrderStatisticsAccessScope scope() {
        return scopeResolver.resolve(
                SaSessionUtil.getLoginUser(),
                SaSessionUtil.getViewMode(),
                WebsiteContext.isWebsiteAdmin(),
                WebsiteContext.getCurrentWebsiteId()
        );
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }
}
