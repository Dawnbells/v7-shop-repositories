package cn.v7soft.admin.statistics;

import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class OrderStatisticsAccessScopeResolver {

    public OrderStatisticsAccessScope resolve(
            SystemUserDto user,
            ViewMode viewMode,
            boolean websiteScoped,
            Long websiteId
    ) {
        Objects.requireNonNull(user, "登录用户不能为空");
        Objects.requireNonNull(user.getCompanyId(), "公司ID不能为空");
        Objects.requireNonNull(user.getUserType(), "用户类型不能为空");
        ViewMode effectiveViewMode = viewMode == null ? ViewMode.TEAM : viewMode;
        if (websiteScoped && websiteId == null) {
            throw new IllegalArgumentException("网站后台缺少网站ID");
        }

        if (effectiveViewMode == ViewMode.PERSONAL
                || user.getUserType() == SystemUserType.EMPLOYEE) {
            return scope(
                    user,
                    false,
                    true,
                    Set.of(),
                    Set.of(),
                    false,
                    websiteScoped,
                    websiteId,
                    effectiveViewMode
            );
        }

        if (user.getUserType() == SystemUserType.ADMIN
                || user.getUserType() == SystemUserType.COMPANY_ADMIN) {
            return scope(
                    user,
                    true,
                    false,
                    Set.of(),
                    Set.of(),
                    true,
                    websiteScoped,
                    websiteId,
                    effectiveViewMode
            );
        }

        if (Boolean.TRUE.equals(user.getIsCrossDepartment())) {
            Set<Long> configuredIds = copyIds(user.getManageDepartmentIds());
            boolean exclude = Boolean.TRUE.equals(user.getIsExcludeDepartment());
            return scope(
                    user,
                    false,
                    false,
                    exclude ? Set.of() : configuredIds,
                    exclude ? configuredIds : Set.of(),
                    false,
                    websiteScoped,
                    websiteId,
                    effectiveViewMode
            );
        }

        Set<Long> allowedDepartmentIds = switch (user.getUserType()) {
            case DEEP_DEPARTMENT_MANAGER -> copyIds(user.getAccessDepartmentIds());
            case DEPARTMENT_MANAGER -> user.getDepartmentId() == null
                    ? Set.of()
                    : Set.of(user.getDepartmentId());
            case DEPARTMENT_TREE -> union(
                    user.getParentDepartmentIds(),
                    user.getAccessDepartmentIds()
            );
            default -> Set.of();
        };

        return scope(
                user,
                false,
                false,
                allowedDepartmentIds,
                Set.of(),
                false,
                websiteScoped,
                websiteId,
                effectiveViewMode
        );
    }

    private OrderStatisticsAccessScope scope(
            SystemUserDto user,
            boolean companyWide,
            boolean personalOnly,
            Set<Long> allowedDepartmentIds,
            Set<Long> excludedDepartmentIds,
            boolean allowUnassigned,
            boolean websiteScoped,
            Long websiteId,
            ViewMode viewMode
    ) {
        return new OrderStatisticsAccessScope(
                user.getCompanyId(),
                user.getLongId(),
                companyWide,
                personalOnly,
                allowedDepartmentIds,
                excludedDepartmentIds,
                allowUnassigned,
                websiteScoped,
                websiteId,
                viewMode
        );
    }

    private Set<Long> union(List<Long> first, List<Long> second) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        result.remove(null);
        return Set.copyOf(result);
    }

    private Set<Long> copyIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>(ids);
        result.remove(null);
        return Set.copyOf(result);
    }
}
