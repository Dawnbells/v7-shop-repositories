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
        boolean forceRefresh
) {
}
