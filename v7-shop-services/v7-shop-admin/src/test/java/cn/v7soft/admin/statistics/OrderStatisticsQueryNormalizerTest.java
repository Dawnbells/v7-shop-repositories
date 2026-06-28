package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatisticsQueryNormalizerTest {

    private final OrderStatisticsQueryNormalizer normalizer =
            new OrderStatisticsQueryNormalizer();

    @Test
    void normalizesEmployeeQueryDomainsIdsAndRates() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.EMPLOYEE);
        request.setEmployeeIds(List.of("101", "102", "101"));
        request.setPlatforms(List.of(WebsiteTypeEnum.V7_SHOP, WebsiteTypeEnum.SHOPLINE));
        request.setDomains(List.of(
                "HTTPS://XL.IWAIW.SHOP:443/path",
                "xl.iwaiw.shop."
        ));
        request.setTemporaryExchangeRates(Map.of("cny", "7.20000000"));

        OrderStatisticsQueryCriteria criteria = normalizer.normalize(
                request,
                companyScope()
        );

        assertThat(criteria.employeeIds()).containsExactly(101L, 102L);
        assertThat(criteria.departmentIds()).isEmpty();
        assertThat(criteria.domains()).containsExactly("xl.iwaiw.shop");
        assertThat(criteria.temporaryExchangeRates())
                .containsEntry("CNY", "7.2");
    }

    @Test
    void rejectsEmployeeAndDepartmentSelectionsAtTheSameTime() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.EMPLOYEE);
        request.setEmployeeIds(List.of("101"));
        request.setDepartmentIds(List.of("10"));

        assertThatThrownBy(() -> normalizer.normalize(request, companyScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("部门");
    }

    @Test
    void requiresAtLeastOneSelectionOrUnassigned() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.DEPARTMENT);

        assertThatThrownBy(() -> normalizer.normalize(request, companyScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少");
    }

    @Test
    void rejectsUnassignedWhenScopeDoesNotAllowIt() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.DEPARTMENT);
        request.setIncludeUnassigned(true);

        assertThatThrownBy(() -> normalizer.normalize(request, departmentScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未归属");
    }

    @Test
    void rejectsDepartmentOutsideAllowedScope() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.DEPARTMENT);
        request.setDepartmentIds(List.of("10", "99"));

        assertThatThrownBy(() -> normalizer.normalize(request, departmentScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限");
    }

    @Test
    void personalScopeCanOnlySelectRequester() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.EMPLOYEE);
        request.setEmployeeIds(List.of("102"));

        assertThatThrownBy(() -> normalizer.normalize(request, personalScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本人");
    }

    @Test
    void selectAllBypassesDepartmentSelectionRequirement() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.DEPARTMENT);
        request.setSelectAll(true);
        // 提交了部门 ID，但全部模式应忽略它们，不按 ID 过滤
        request.setDepartmentIds(List.of("10"));

        OrderStatisticsQueryCriteria criteria = normalizer.normalize(request, companyScope());

        assertThat(criteria.selectAll()).isTrue();
        assertThat(criteria.departmentIds()).isEmpty();
    }

    @Test
    void selectAllBypassesEmployeeSelectionRequirement() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.EMPLOYEE);
        request.setSelectAll(true);

        OrderStatisticsQueryCriteria criteria = normalizer.normalize(request, companyScope());

        assertThat(criteria.selectAll()).isTrue();
        assertThat(criteria.employeeIds()).isEmpty();
    }

    @Test
    void personalScopeRejectsSelectAll() {
        OrderStatisticsQueryRequest request = baseRequest();
        request.setDimension(OrderStatisticsDimension.EMPLOYEE);
        request.setSelectAll(true);

        assertThatThrownBy(() -> normalizer.normalize(request, personalScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本人");
    }

    @Test
    void rejectsInvalidIdAndTemporaryRate() {
        OrderStatisticsQueryRequest invalidId = baseRequest();
        invalidId.setDimension(OrderStatisticsDimension.EMPLOYEE);
        invalidId.setEmployeeIds(List.of("not-an-id"));
        assertThatThrownBy(() -> normalizer.normalize(invalidId, companyScope()))
                .hasMessageContaining("ID");

        OrderStatisticsQueryRequest invalidRate = baseRequest();
        invalidRate.setDimension(OrderStatisticsDimension.EMPLOYEE);
        invalidRate.setEmployeeIds(List.of("101"));
        invalidRate.setTemporaryExchangeRates(Map.of("CNY", "0"));
        assertThatThrownBy(() -> normalizer.normalize(invalidRate, companyScope()))
                .hasMessageContaining("汇率");
    }

    private OrderStatisticsQueryRequest baseRequest() {
        return OrderStatisticsQueryRequest.builder()
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2026-06-24"))
                .granularity(OrderStatisticsGranularity.DAY)
                .targetCurrencyCode("usd")
                .employeeIds(List.of())
                .departmentIds(List.of())
                .platforms(List.of())
                .domains(List.of())
                .temporaryExchangeRates(Map.of())
                .build();
    }

    private OrderStatisticsAccessScope companyScope() {
        return new OrderStatisticsAccessScope(
                9L, 101L, true, false, Set.of(), Set.of(),
                true, false, null, ViewMode.TEAM
        );
    }

    private OrderStatisticsAccessScope departmentScope() {
        return new OrderStatisticsAccessScope(
                9L, 101L, false, false, Set.of(10L, 11L), Set.of(),
                false, false, null, ViewMode.TEAM
        );
    }

    private OrderStatisticsAccessScope personalScope() {
        return new OrderStatisticsAccessScope(
                9L, 101L, false, true, Set.of(), Set.of(),
                false, false, null, ViewMode.PERSONAL
        );
    }
}
