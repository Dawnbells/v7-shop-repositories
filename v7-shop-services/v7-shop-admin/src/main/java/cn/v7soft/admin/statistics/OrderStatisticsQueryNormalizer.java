package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.WebsiteTypeEnum;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class OrderStatisticsQueryNormalizer {

    private static final BigDecimal MAX_RATE = new BigDecimal("1000000000");
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Za-z]{3}$");

    public OrderStatisticsQueryCriteria normalize(
            OrderStatisticsQueryRequest request,
            OrderStatisticsAccessScope scope
    ) {
        Objects.requireNonNull(request, "查询条件不能为空");
        Objects.requireNonNull(scope, "数据权限不能为空");
        Objects.requireNonNull(request.getStartDate(), "开始日期不能为空");
        Objects.requireNonNull(request.getEndDate(), "结束日期不能为空");
        Objects.requireNonNull(request.getGranularity(), "时间粒度不能为空");
        Objects.requireNonNull(request.getDimension(), "统计维度不能为空");

        List<Long> employeeIds = normalizeIds(request.getEmployeeIds());
        List<Long> departmentIds = normalizeIds(request.getDepartmentIds());
        boolean includeUnassigned = Boolean.TRUE.equals(request.getIncludeUnassigned());
        boolean selectAll = Boolean.TRUE.equals(request.getSelectAll());

        if (request.getDimension() == OrderStatisticsDimension.EMPLOYEE) {
            if (!departmentIds.isEmpty()) {
                throw new IllegalArgumentException("按员工统计时不能提交部门条件");
            }
            // 全部模式不按 ID 过滤，无需选择具体员工；个人模式仍只能统计本人（全部模式由下方拦截）
            if (!selectAll && employeeIds.isEmpty() && !includeUnassigned) {
                throw new IllegalArgumentException("至少选择一个员工或未归属");
            }
            validatePersonalSelection(scope, employeeIds, includeUnassigned, selectAll);
        } else {
            if (!employeeIds.isEmpty()) {
                throw new IllegalArgumentException("按部门统计时不能提交员工条件");
            }
            if (!selectAll && departmentIds.isEmpty() && !includeUnassigned) {
                throw new IllegalArgumentException("至少选择一个部门或未归属");
            }
            validateDepartmentSelection(scope, departmentIds);
        }

        if (includeUnassigned && !scope.allowUnassigned()) {
            throw new IllegalArgumentException("当前用户无权统计未归属订单");
        }

        return new OrderStatisticsQueryCriteria(
                request.getStartDate(),
                request.getEndDate(),
                request.getGranularity(),
                request.getDimension(),
                // 全部模式忽略具体 ID（SQL 不按 ID 过滤），避免预置选中分组造成口径混淆
                selectAll ? List.of() : List.copyOf(employeeIds),
                selectAll ? List.of() : List.copyOf(departmentIds),
                includeUnassigned,
                normalizePlatforms(request.getPlatforms()),
                normalizeDomains(request.getDomains()),
                normalizeCurrencyCode(request.getTargetCurrencyCode()),
                normalizeRates(request.getTemporaryExchangeRates()),
                Boolean.TRUE.equals(request.getForceRefresh()),
                selectAll
        );
    }

    private void validatePersonalSelection(
            OrderStatisticsAccessScope scope,
            List<Long> employeeIds,
            boolean includeUnassigned,
            boolean selectAll
    ) {
        if (!scope.personalOnly()) {
            return;
        }
        // 个人模式只能统计本人：不允许「全部」「未归属」，且必须且仅选自己
        if (selectAll
                || includeUnassigned
                || employeeIds.size() != 1
                || employeeIds.get(0) != scope.requesterUserId()) {
            throw new IllegalArgumentException("个人模式只能统计本人");
        }
    }

    private void validateDepartmentSelection(
            OrderStatisticsAccessScope scope,
            List<Long> departmentIds
    ) {
        if (scope.companyWide()) {
            return;
        }
        if (scope.personalOnly()) {
            throw new IllegalArgumentException("个人模式不能按部门统计");
        }
        if (!scope.excludedDepartmentIds().isEmpty()) {
            boolean containsExcluded = departmentIds.stream()
                    .anyMatch(scope.excludedDepartmentIds()::contains);
            if (containsExcluded) {
                throw new IllegalArgumentException("选择了权限范围外的部门");
            }
            return;
        }
        if (!scope.allowedDepartmentIds().containsAll(departmentIds)) {
            throw new IllegalArgumentException("选择了权限范围外的部门");
        }
    }

    private List<Long> normalizeIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            try {
                long id = Long.parseLong(rawId);
                if (id <= 0) {
                    throw new NumberFormatException("non-positive");
                }
                result.add(id);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("ID格式不正确");
            }
        }
        return new ArrayList<>(result);
    }

    private List<WebsiteTypeEnum> normalizePlatforms(List<WebsiteTypeEnum> platforms) {
        return platforms == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(platforms));
    }

    private List<String> normalizeDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String domain : domains) {
            result.add(normalizeDomain(domain));
        }
        return List.copyOf(result);
    }

    private String normalizeDomain(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            throw new IllegalArgumentException("域名不能为空");
        }
        String candidate = rawDomain.trim();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }
        try {
            URI uri = new URI(candidate);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("域名格式不正确");
            }
            host = host.toLowerCase(Locale.ROOT);
            while (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            if (host.isBlank()) {
                throw new IllegalArgumentException("域名格式不正确");
            }
            return host;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("域名格式不正确");
        }
    }

    private String normalizeCurrencyCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("目标币种不能为空");
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("币种代码格式不正确");
        }
        return code;
    }

    private Map<String, String> normalizeRates(Map<String, String> rawRates) {
        if (rawRates == null || rawRates.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawRates.entrySet()) {
            String code = normalizeCurrencyCode(entry.getKey());
            result.put(code, normalizeRate(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private String normalizeRate(String rawRate) {
        try {
            if (rawRate == null || rawRate.isBlank()) {
                throw new IllegalArgumentException("临时汇率不能为空");
            }
            BigDecimal rate = new BigDecimal(rawRate.trim());
            BigDecimal normalized = rate.stripTrailingZeros();
            if (rate.compareTo(BigDecimal.ZERO) <= 0
                    || rate.compareTo(MAX_RATE) > 0
                    || Math.max(0, normalized.scale()) > 8) {
                throw new IllegalArgumentException("临时汇率格式不正确");
            }
            return normalized.toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("临时汇率格式不正确");
        }
    }
}
