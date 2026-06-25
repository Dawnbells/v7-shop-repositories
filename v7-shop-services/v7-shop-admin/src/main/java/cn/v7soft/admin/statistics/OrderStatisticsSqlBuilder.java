package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.WebsiteTypeEnum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderStatisticsSqlBuilder {

    public OrderStatisticsSqlPlan build(
            List<OrderStatisticsBucket> buckets,
            OrderStatisticsQueryCriteria criteria,
            OrderStatisticsAccessScope scope
    ) {
        if (buckets == null || buckets.isEmpty()) {
            throw new IllegalArgumentException("时间桶不能为空");
        }

        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        String bucketCte = buildBucketCte(buckets, parameters);
        String groupId = criteria.dimension() == OrderStatisticsDimension.EMPLOYEE
                ? "ci.sales_uid"
                : "ci.department_id";
        String groupName = criteria.dimension() == OrderStatisticsDimension.EMPLOYEE
                ? "ci.sales_person"
                : "ci.department";

        List<String> conditions = new ArrayList<>();
        conditions.add("o.company_id = :companyId");
        conditions.add("o.status <> 'DELETED'");
        parameters.put("companyId", scope.companyId());

        addPermissionCondition(conditions, parameters, scope);
        addWebsiteCondition(conditions, parameters, scope);
        addDimensionSelection(conditions, parameters, criteria);
        addPlatforms(conditions, parameters, criteria.platforms());
        addDomains(conditions, parameters, criteria.domains());

        String sql = """
                WITH bucket_ranges AS (
                %s
                )
                SELECT
                    b.bucket_key,
                    %s AS group_id,
                    MAX(%s) AS group_name,
                    ci.currency_code,
                    ci.currency_exchange_rate,
                    o.order_status,
                    COUNT(*) AS order_count,
                    COALESCE(SUM(o.total_amount), 0) AS original_amount
                FROM bucket_ranges b
                JOIN t_orders o
                  ON o.order_time >= b.bucket_start
                 AND o.order_time < b.bucket_end
                LEFT JOIN t_order_context_infos ci ON ci.id = o.context_info_id
                LEFT JOIN t_system_users owner_user ON owner_user.id = o.user_id
                WHERE %s
                GROUP BY
                    b.bucket_key,
                    %s,
                    ci.currency_code,
                    ci.currency_exchange_rate,
                    o.order_status
                ORDER BY b.bucket_key ASC, group_id ASC
                """.formatted(
                bucketCte,
                groupId,
                groupName,
                String.join("\n  AND ", conditions),
                groupId
        );
        return new OrderStatisticsSqlPlan(sql, Map.copyOf(parameters));
    }

    private String buildBucketCte(
            List<OrderStatisticsBucket> buckets,
            Map<String, Object> parameters
    ) {
        List<String> selects = new ArrayList<>();
        for (int index = 0; index < buckets.size(); index++) {
            OrderStatisticsBucket bucket = buckets.get(index);
            parameters.put("bucketKey" + index, bucket.key());
            parameters.put("bucketStart" + index, bucket.queryStart());
            parameters.put("bucketEnd" + index, bucket.queryEnd());
            String select = (index == 0 ? "SELECT " : "UNION ALL SELECT ")
                    + ":bucketKey" + index + " AS bucket_key, "
                    + ":bucketStart" + index + " AS bucket_start, "
                    + ":bucketEnd" + index + " AS bucket_end";
            selects.add(select);
        }
        return String.join("\n", selects);
    }

    private void addPermissionCondition(
            List<String> conditions,
            Map<String, Object> parameters,
            OrderStatisticsAccessScope scope
    ) {
        if (scope.personalOnly()) {
            conditions.add("(ci.sales_uid = :requesterUserId OR o.user_id = :requesterUserId)");
            parameters.put("requesterUserId", scope.requesterUserId());
            return;
        }
        if (scope.companyWide()) {
            return;
        }
        String departmentExpression = "COALESCE(ci.department_id, owner_user.department_id)";
        if (!scope.excludedDepartmentIds().isEmpty()) {
            conditions.add(departmentExpression + " NOT IN ("
                    + addParameters(parameters, "excludedDepartment", scope.excludedDepartmentIds())
                    + ")");
            return;
        }
        if (!scope.allowedDepartmentIds().isEmpty()) {
            conditions.add(departmentExpression + " IN ("
                    + addParameters(parameters, "allowedDepartment", scope.allowedDepartmentIds())
                    + ")");
            return;
        }
        conditions.add("1 = 0");
    }

    private void addWebsiteCondition(
            List<String> conditions,
            Map<String, Object> parameters,
            OrderStatisticsAccessScope scope
    ) {
        if (scope.websiteScoped()) {
            conditions.add("ci.website_id = :websiteId");
            parameters.put("websiteId", scope.websiteId());
        }
    }

    private void addDimensionSelection(
            List<String> conditions,
            Map<String, Object> parameters,
            OrderStatisticsQueryCriteria criteria
    ) {
        String expression;
        List<Long> selectedIds;
        String parameterPrefix;
        if (criteria.dimension() == OrderStatisticsDimension.EMPLOYEE) {
            expression = "ci.sales_uid";
            selectedIds = criteria.employeeIds();
            parameterPrefix = "selectedEmployee";
        } else {
            expression = "ci.department_id";
            selectedIds = criteria.departmentIds();
            parameterPrefix = "selectedDepartment";
        }

        List<String> selectionParts = new ArrayList<>();
        if (!selectedIds.isEmpty()) {
            selectionParts.add(expression + " IN ("
                    + addParameters(parameters, parameterPrefix, selectedIds)
                    + ")");
        }
        if (criteria.includeUnassigned()) {
            selectionParts.add(expression + " IS NULL");
        }
        conditions.add("(" + String.join(" OR ", selectionParts) + ")");
    }

    private void addPlatforms(
            List<String> conditions,
            Map<String, Object> parameters,
            List<WebsiteTypeEnum> platforms
    ) {
        if (platforms == null || platforms.isEmpty()) {
            return;
        }
        List<String> values = platforms.stream().map(Enum::name).toList();
        conditions.add("o.platform IN (" + addParameters(parameters, "platform", values) + ")");
    }

    private void addDomains(
            List<String> conditions,
            Map<String, Object> parameters,
            List<String> domains
    ) {
        if (domains == null || domains.isEmpty()) {
            return;
        }
        conditions.add("LOWER(ci.website_url) IN ("
                + addParameters(parameters, "domain", domains)
                + ")");
    }

    private String addParameters(
            Map<String, Object> parameters,
            String prefix,
            Collection<?> values
    ) {
        List<String> names = new ArrayList<>();
        int index = 0;
        for (Object value : values) {
            String name = prefix + index++;
            parameters.put(name, value);
            names.add(":" + name);
        }
        return String.join(", ", names);
    }
}
