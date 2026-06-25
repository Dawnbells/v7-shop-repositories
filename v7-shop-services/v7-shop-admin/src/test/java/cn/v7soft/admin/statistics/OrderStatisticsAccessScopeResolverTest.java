package cn.v7soft.admin.statistics;

import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsAccessScopeResolverTest {

    private final OrderStatisticsAccessScopeResolver resolver =
            new OrderStatisticsAccessScopeResolver();

    @Test
    void adminTeamModeHasCompanyWideScopeAndCanSelectUnassigned() {
        OrderStatisticsAccessScope scope = resolver.resolve(
                user(SystemUserType.ADMIN),
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.companyWide()).isTrue();
        assertThat(scope.personalOnly()).isFalse();
        assertThat(scope.allowUnassigned()).isTrue();
        assertThat(scope.allowedDepartmentIds()).isEmpty();
    }

    @Test
    void companyAdminWebsiteModeIsCompanyWideInsideCurrentWebsite() {
        OrderStatisticsAccessScope scope = resolver.resolve(
                user(SystemUserType.COMPANY_ADMIN),
                ViewMode.TEAM,
                true,
                88L
        );

        assertThat(scope.companyWide()).isTrue();
        assertThat(scope.websiteScoped()).isTrue();
        assertThat(scope.websiteId()).isEqualTo(88L);
    }

    @Test
    void personalViewModeAlwaysRestrictsToRequester() {
        OrderStatisticsAccessScope scope = resolver.resolve(
                user(SystemUserType.ADMIN),
                ViewMode.PERSONAL,
                false,
                null
        );

        assertThat(scope.companyWide()).isFalse();
        assertThat(scope.personalOnly()).isTrue();
        assertThat(scope.allowUnassigned()).isFalse();
    }

    @Test
    void deepDepartmentManagerUsesCurrentAndDescendantDepartments() {
        SystemUserDto user = user(SystemUserType.DEEP_DEPARTMENT_MANAGER);
        user.setAccessDepartmentIds(List.of(10L, 11L, 12L));

        OrderStatisticsAccessScope scope = resolver.resolve(
                user,
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.allowedDepartmentIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(scope.allowUnassigned()).isFalse();
    }

    @Test
    void departmentManagerUsesOnlyCurrentDepartment() {
        SystemUserDto user = user(SystemUserType.DEPARTMENT_MANAGER);
        user.setDepartmentId(10L);

        OrderStatisticsAccessScope scope = resolver.resolve(
                user,
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.allowedDepartmentIds()).containsExactly(10L);
    }

    @Test
    void departmentTreeUsesParentCurrentAndDescendantDepartments() {
        SystemUserDto user = user(SystemUserType.DEPARTMENT_TREE);
        user.setParentDepartmentIds(List.of(1L, 2L));
        user.setAccessDepartmentIds(List.of(3L, 4L));

        OrderStatisticsAccessScope scope = resolver.resolve(
                user,
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.allowedDepartmentIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void employeeIsAlwaysPersonalOnly() {
        OrderStatisticsAccessScope scope = resolver.resolve(
                user(SystemUserType.EMPLOYEE),
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.personalOnly()).isTrue();
        assertThat(scope.companyWide()).isFalse();
    }

    @Test
    void crossDepartmentIncludeUsesConfiguredDepartments() {
        SystemUserDto user = user(SystemUserType.DEPARTMENT_MANAGER);
        user.setIsCrossDepartment(true);
        user.setManageDepartmentIds(List.of(20L, 21L));
        user.setIsExcludeDepartment(false);

        OrderStatisticsAccessScope scope = resolver.resolve(
                user,
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.allowedDepartmentIds()).containsExactlyInAnyOrder(20L, 21L);
        assertThat(scope.excludedDepartmentIds()).isEmpty();
    }

    @Test
    void crossDepartmentExcludeUsesExcludedDepartments() {
        SystemUserDto user = user(SystemUserType.DEPARTMENT_MANAGER);
        user.setIsCrossDepartment(true);
        user.setManageDepartmentIds(List.of(20L, 21L));
        user.setIsExcludeDepartment(true);

        OrderStatisticsAccessScope scope = resolver.resolve(
                user,
                ViewMode.TEAM,
                false,
                null
        );

        assertThat(scope.allowedDepartmentIds()).isEmpty();
        assertThat(scope.excludedDepartmentIds()).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    void websiteScopeRequiresWebsiteId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(
                user(SystemUserType.ADMIN),
                ViewMode.TEAM,
                true,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private SystemUserDto user(SystemUserType type) {
        return SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .userType(type)
                .accessDepartmentIds(List.of())
                .parentDepartmentIds(List.of())
                .manageDepartmentIds(List.of())
                .build();
    }
}
