package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.ViewMode;

import java.util.Set;

public record OrderStatisticsAccessScope(
        long companyId,
        long requesterUserId,
        boolean companyWide,
        boolean personalOnly,
        Set<Long> allowedDepartmentIds,
        Set<Long> excludedDepartmentIds,
        boolean allowUnassigned,
        boolean websiteScoped,
        Long websiteId,
        ViewMode viewMode
) {

    public OrderStatisticsAccessScope {
        allowedDepartmentIds = allowedDepartmentIds == null
                ? Set.of()
                : Set.copyOf(allowedDepartmentIds);
        excludedDepartmentIds = excludedDepartmentIds == null
                ? Set.of()
                : Set.copyOf(excludedDepartmentIds);
    }
}
