package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.WebsiteTypeEnum;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OrderStatisticsQueryCriteria(
        LocalDate startDate,
        LocalDate endDate,
        OrderStatisticsGranularity granularity,
        OrderStatisticsDimension dimension,
        List<Long> employeeIds,
        List<Long> departmentIds,
        boolean includeUnassigned,
        List<WebsiteTypeEnum> platforms,
        List<String> domains,
        String targetCurrencyCode,
        Map<String, String> temporaryExchangeRates,
        boolean forceRefresh,
        // 全部模式：不按员工/部门 ID 过滤，仅受数据权限/平台/域名约束，口径等同订单管理。
        // 用于把「归属已失效」（部门/销售员已删除）及未归属的订单全量纳入统计。
        boolean selectAll
) {

    /**
     * 兼容旧调用点（不含 selectAll）的便捷构造器，默认 selectAll=false（按 ID 过滤的常规口径）。
     */
    public OrderStatisticsQueryCriteria(
            LocalDate startDate,
            LocalDate endDate,
            OrderStatisticsGranularity granularity,
            OrderStatisticsDimension dimension,
            List<Long> employeeIds,
            List<Long> departmentIds,
            boolean includeUnassigned,
            List<WebsiteTypeEnum> platforms,
            List<String> domains,
            String targetCurrencyCode,
            Map<String, String> temporaryExchangeRates,
            boolean forceRefresh
    ) {
        this(startDate, endDate, granularity, dimension, employeeIds, departmentIds,
                includeUnassigned, platforms, domains, targetCurrencyCode,
                temporaryExchangeRates, forceRefresh, false);
    }
}
