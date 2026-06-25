package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatisticsSqlBuilderTest {

    private final OrderStatisticsSqlBuilder builder = new OrderStatisticsSqlBuilder();

    @Test
    void buildsEmployeeCompanyWideQueryWithWebsitePlatformAndDomainFilters() {
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L, 102L),
                List.of(),
                true,
                List.of(WebsiteTypeEnum.V7_SHOP, WebsiteTypeEnum.SHOPLINE),
                List.of("a.example.com", "b.example.com"),
                "USD",
                Map.of(),
                false
        );
        OrderStatisticsAccessScope scope = new OrderStatisticsAccessScope(
                9L, 1L, true, false, Set.of(), Set.of(),
                true, true, 88L, ViewMode.TEAM
        );

        OrderStatisticsSqlPlan plan = builder.build(
                List.of(bucket()),
                criteria,
                scope
        );

        assertThat(plan.sql())
                .contains("o.company_id = :companyId")
                .contains("o.status <> 'DELETED'")
                .contains("ci.website_id = :websiteId")
                .contains("ci.sales_uid IN (:selectedEmployee0, :selectedEmployee1)")
                .contains("OR ci.sales_uid IS NULL")
                .contains("o.platform IN (:platform0, :platform1)")
                .contains("LOWER(ci.website_url) IN (:domain0, :domain1)");
        assertThat(plan.parameters())
                .containsEntry("companyId", 9L)
                .containsEntry("websiteId", 88L)
                .containsEntry("selectedEmployee0", 101L)
                .containsEntry("domain0", "a.example.com");
    }

    @Test
    void buildsDepartmentPermissionAndSelectionAsSeparateConditions() {
        OrderStatisticsQueryCriteria criteria = new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.DEPARTMENT,
                List.of(),
                List.of(10L),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
        OrderStatisticsAccessScope scope = new OrderStatisticsAccessScope(
                9L, 1L, false, false, Set.of(10L, 11L), Set.of(),
                false, false, null, ViewMode.TEAM
        );

        OrderStatisticsSqlPlan plan = builder.build(
                List.of(bucket()),
                criteria,
                scope
        );

        assertThat(plan.sql())
                .contains("COALESCE(ci.department_id, owner_user.department_id) IN (:allowedDepartment0, :allowedDepartment1)")
                .contains("ci.department_id IN (:selectedDepartment0)");
    }

    @Test
    void buildsPersonalAndExcludedDepartmentPermissions() {
        OrderStatisticsQueryCriteria employeeCriteria = employeeCriteria();
        OrderStatisticsSqlPlan personal = builder.build(
                List.of(bucket()),
                employeeCriteria,
                new OrderStatisticsAccessScope(
                        9L, 101L, false, true, Set.of(), Set.of(),
                        false, false, null, ViewMode.PERSONAL
                )
        );
        assertThat(personal.sql())
                .contains("(ci.sales_uid = :requesterUserId OR o.user_id = :requesterUserId)");

        OrderStatisticsSqlPlan excluded = builder.build(
                List.of(bucket()),
                employeeCriteria,
                new OrderStatisticsAccessScope(
                        9L, 101L, false, false, Set.of(), Set.of(20L, 21L),
                        false, false, null, ViewMode.TEAM
                )
        );
        assertThat(excluded.sql())
                .contains("COALESCE(ci.department_id, owner_user.department_id) NOT IN (:excludedDepartment0, :excludedDepartment1)");
    }

    private OrderStatisticsQueryCriteria employeeCriteria() {
        return new OrderStatisticsQueryCriteria(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-01"),
                OrderStatisticsGranularity.DAY,
                OrderStatisticsDimension.EMPLOYEE,
                List.of(101L),
                List.of(),
                false,
                List.of(),
                List.of(),
                "USD",
                Map.of(),
                false
        );
    }

    private OrderStatisticsBucket bucket() {
        return new OrderStatisticsBucket(
                "2026-06-01",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z"),
                LocalDateTime.parse("2026-06-01T08:00:00"),
                LocalDateTime.parse("2026-06-02T08:00:00"),
                false
        );
    }
}
