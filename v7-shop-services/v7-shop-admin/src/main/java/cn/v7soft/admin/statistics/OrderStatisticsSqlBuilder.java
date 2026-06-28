package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsDimension;
import cn.v7soft.dao.enums.WebsiteTypeEnum;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderStatisticsSqlBuilder {

    /**
     * 时间范围参数以「数据库墙钟字符串」绑定，而不是 LocalDateTime。
     * 原因：order_time 是 Asia/Shanghai 墙钟存储，桶边界 queryStart/queryEnd 也是该口径的
     * LocalDateTime；但原生查询 setParameter(LocalDateTime) 在绑定时会按 JVM 默认时区做转换，
     * 若运行机时区不是 Asia/Shanghai（如美西 LA），过滤窗口会整体平移十几小时，导致计数错误。
     * 绑定为 'yyyy-MM-dd HH:mm:ss.SSSSSS' 字符串后，MySQL 与 datetime 列按字面比较，与 JVM 时区无关。
     */
    private static final DateTimeFormatter SQL_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static String toSqlTimestamp(LocalDateTime value) {
        return value.format(SQL_TIMESTAMP);
    }

    public OrderStatisticsSqlPlan build(
            List<OrderStatisticsBucket> buckets,
            OrderStatisticsQueryCriteria criteria,
            OrderStatisticsAccessScope scope
    ) {
        if (buckets == null || buckets.isEmpty()) {
            throw new IllegalArgumentException("时间桶不能为空");
        }

        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        String bucketKeyExpression = buildBucketCase(buckets, parameters);
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

        // 全局时间范围使用常量边界，可命中 order_time 索引做单次范围扫描；
        // 桶之间连续无缝（见 OrderStatisticsBucketFactory），所以 [rangeStart, rangeEnd)
        // 恰好等于所有桶区间的并集，每行落在唯一的桶里，bucketKey 表达式不会产生 NULL。
        conditions.add("o.order_time >= :rangeStart");
        conditions.add("o.order_time < :rangeEnd");
        parameters.put("rangeStart", toSqlTimestamp(buckets.get(0).queryStart()));
        parameters.put("rangeEnd", toSqlTimestamp(buckets.get(buckets.size() - 1).queryEnd()));

        addPermissionCondition(conditions, parameters, scope);
        addWebsiteCondition(conditions, parameters, scope);
        addDimensionSelection(conditions, parameters, criteria);
        addPlatforms(conditions, parameters, criteria.platforms());
        addDomains(conditions, parameters, criteria.domains());

        // 旧实现把每个桶作为一行 UNION ALL，再用范围条件 JOIN t_orders；MySQL 会先把订单
        // 全表扫描物化，再与 N 个桶行做无条件 hash join（笛卡尔积 N × 订单数）后才过滤时间，
        // 复杂度 O(桶数 × 订单数)，order_time 索引完全用不上。这里改为对 t_orders 单次范围
        // 扫描，桶归属由 CASE 在每行上就地计算，复杂度降为 O(范围内订单数)。
        String sql = """
                SELECT
                    %s AS bucket_key,
                    %s AS group_id,
                    MAX(%s) AS group_name,
                    ci.currency_code,
                    ci.currency_exchange_rate,
                    o.order_status,
                    COUNT(*) AS order_count,
                    COALESCE(SUM(o.total_amount), 0) AS original_amount
                FROM t_orders o
                LEFT JOIN t_order_context_infos ci ON ci.id = o.context_info_id
                LEFT JOIN t_system_users owner_user ON owner_user.id = o.user_id
                WHERE %s
                GROUP BY
                    bucket_key,
                    %s,
                    ci.currency_code,
                    ci.currency_exchange_rate,
                    o.order_status
                ORDER BY bucket_key ASC, group_id ASC
                """.formatted(
                bucketKeyExpression,
                groupId,
                groupName,
                String.join("\n  AND ", conditions),
                groupId
        );
        return new OrderStatisticsSqlPlan(sql, Map.copyOf(parameters));
    }

    private String buildBucketCase(
            List<OrderStatisticsBucket> buckets,
            Map<String, Object> parameters
    ) {
        StringBuilder caseExpression = new StringBuilder("CASE");
        for (int index = 0; index < buckets.size(); index++) {
            OrderStatisticsBucket bucket = buckets.get(index);
            parameters.put("bucketKey" + index, bucket.key());
            parameters.put("bucketStart" + index, toSqlTimestamp(bucket.queryStart()));
            parameters.put("bucketEnd" + index, toSqlTimestamp(bucket.queryEnd()));
            caseExpression
                    .append("\n        WHEN o.order_time >= :bucketStart").append(index)
                    .append(" AND o.order_time < :bucketEnd").append(index)
                    .append(" THEN :bucketKey").append(index);
        }
        caseExpression.append("\n    END");
        return caseExpression.toString();
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
