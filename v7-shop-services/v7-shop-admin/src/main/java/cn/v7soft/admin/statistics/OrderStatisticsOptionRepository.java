package cn.v7soft.admin.statistics;

import cn.v7soft.admin.controller.resp.OrderStatisticsOptionResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class OrderStatisticsOptionRepository {

    private final EntityManager entityManager;

    public OrderStatisticsOptionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<OrderStatisticsOptionResponse> employees(
            OrderStatisticsAccessScope scope,
            String keyword,
            boolean includeHistorical
    ) {
        LinkedHashMap<String, OrderStatisticsOptionResponse> result = new LinkedHashMap<>();
        currentEmployees(scope, keyword, includeHistorical)
                .forEach(option -> result.put(option.getId(), option));
        if (includeHistorical) {
            historicalEmployees(scope, keyword).forEach(option ->
                    result.putIfAbsent(option.getId(), option)
            );
        }
        return List.copyOf(result.values());
    }

    public List<OrderStatisticsOptionResponse> departments(
            OrderStatisticsAccessScope scope,
            String keyword,
            boolean includeHistorical
    ) {
        LinkedHashMap<String, OrderStatisticsOptionResponse> result = new LinkedHashMap<>();
        currentDepartments(scope, keyword, includeHistorical)
                .forEach(option -> result.put(option.getId(), option));
        if (includeHistorical) {
            historicalDepartments(scope, keyword).forEach(option ->
                    result.putIfAbsent(option.getId(), option)
            );
        }
        return List.copyOf(result.values());
    }

    public List<String> domains(OrderStatisticsAccessScope scope, String keyword) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = orderConditions(scope, parameters);
        conditions.add("ci.website_url IS NOT NULL");
        conditions.add("TRIM(ci.website_url) <> ''");
        if (hasText(keyword)) {
            conditions.add("LOWER(ci.website_url) LIKE :keyword");
            parameters.put("keyword", "%" + normalizeKeyword(keyword) + "%");
        }
        String sql = """
                SELECT DISTINCT LOWER(TRIM(ci.website_url))
                FROM t_orders o
                LEFT JOIN t_order_context_infos ci ON ci.id = o.context_info_id
                LEFT JOIN t_system_users owner_user ON owner_user.id = o.user_id
                WHERE %s
                ORDER BY LOWER(TRIM(ci.website_url))
                LIMIT 200
                """.formatted(String.join("\n  AND ", conditions));
        return executeScalar(sql, parameters).stream()
                .map(String::valueOf)
                .toList();
    }

    private List<OrderStatisticsOptionResponse> currentEmployees(
            OrderStatisticsAccessScope scope,
            String keyword,
            boolean includeHistorical
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = new ArrayList<>();
        conditions.add("u.company_id = :companyId");
        parameters.put("companyId", scope.companyId());
        addCurrentDepartmentPermission(conditions, parameters, scope, "u.department_id", "u.id");
        if (!includeHistorical) {
            conditions.add("u.status = 'VALID'");
            conditions.add("u.hidden = FALSE");
        }
        if (hasText(keyword)) {
            conditions.add("(LOWER(u.name) LIKE :keyword"
                    + " OR LOWER(u.telephone) LIKE :keyword"
                    + " OR CAST(u.id AS CHAR) LIKE :keyword)");
            parameters.put("keyword", "%" + normalizeKeyword(keyword) + "%");
        }
        String sql = """
                SELECT u.id, u.name, u.telephone, u.department_id, d.name, u.status, u.hidden
                FROM t_system_users u
                LEFT JOIN t_department d ON d.id = u.department_id
                WHERE %s
                ORDER BY u.name, u.id
                LIMIT 200
                """.formatted(String.join("\n  AND ", conditions));
        return execute(sql, parameters).stream()
                .map(row -> OrderStatisticsOptionResponse.builder()
                        .id(stringId(row[0]))
                        .name(stringValue(row[1]))
                        .telephone(stringValue(row[2]))
                        .departmentId(stringId(row[3]))
                        .departmentName(stringValue(row[4]))
                        .status(stringValue(row[5]))
                        .historical(!"VALID".equals(stringValue(row[5])) || booleanValue(row[6]))
                        .disabled(!"VALID".equals(stringValue(row[5])) || booleanValue(row[6]))
                        .build())
                .toList();
    }

    private List<OrderStatisticsOptionResponse> historicalEmployees(
            OrderStatisticsAccessScope scope,
            String keyword
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = orderConditions(scope, parameters);
        conditions.add("ci.sales_uid IS NOT NULL");
        if (hasText(keyword)) {
            conditions.add("(LOWER(COALESCE(u.name, ci.sales_person)) LIKE :keyword"
                    + " OR LOWER(COALESCE(u.telephone, '')) LIKE :keyword"
                    + " OR CAST(ci.sales_uid AS CHAR) LIKE :keyword)");
            parameters.put("keyword", "%" + normalizeKeyword(keyword) + "%");
        }
        String sql = """
                SELECT
                    ci.sales_uid,
                    MAX(COALESCE(u.name, ci.sales_person)),
                    MAX(u.telephone),
                    MAX(ci.department_id),
                    MAX(COALESCE(d.name, ci.department)),
                    MAX(u.status),
                    MAX(COALESCE(u.hidden, TRUE))
                FROM t_orders o
                LEFT JOIN t_order_context_infos ci ON ci.id = o.context_info_id
                LEFT JOIN t_system_users owner_user ON owner_user.id = o.user_id
                LEFT JOIN t_system_users u ON u.id = ci.sales_uid
                LEFT JOIN t_department d ON d.id = ci.department_id
                WHERE %s
                GROUP BY ci.sales_uid
                ORDER BY MAX(COALESCE(u.name, ci.sales_person)), ci.sales_uid
                LIMIT 200
                """.formatted(String.join("\n  AND ", conditions));
        return execute(sql, parameters).stream()
                .map(row -> OrderStatisticsOptionResponse.builder()
                        .id(stringId(row[0]))
                        .name(stringValue(row[1]))
                        .telephone(stringValue(row[2]))
                        .departmentId(stringId(row[3]))
                        .departmentName(stringValue(row[4]))
                        .status(stringValue(row[5]))
                        .historical(true)
                        .disabled(true)
                        .build())
                .toList();
    }

    private List<OrderStatisticsOptionResponse> currentDepartments(
            OrderStatisticsAccessScope scope,
            String keyword,
            boolean includeHistorical
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = new ArrayList<>();
        conditions.add("d.company_id = :companyId");
        parameters.put("companyId", scope.companyId());
        addCurrentDepartmentPermission(conditions, parameters, scope, "d.id", null);
        if (!includeHistorical) {
            conditions.add("d.status = 'VALID'");
        }
        if (hasText(keyword)) {
            conditions.add("LOWER(d.name) LIKE :keyword");
            parameters.put("keyword", "%" + normalizeKeyword(keyword) + "%");
        }
        String sql = """
                SELECT d.id, d.name, d.parent_id, d.status
                FROM t_department d
                WHERE %s
                ORDER BY d.sort_order, d.name, d.id
                LIMIT 500
                """.formatted(String.join("\n  AND ", conditions));
        return execute(sql, parameters).stream()
                .map(row -> OrderStatisticsOptionResponse.builder()
                        .id(stringId(row[0]))
                        .name(stringValue(row[1]))
                        .parentId(stringId(row[2]))
                        .status(stringValue(row[3]))
                        .historical(!"VALID".equals(stringValue(row[3])))
                        .disabled(!"VALID".equals(stringValue(row[3])))
                        .build())
                .toList();
    }

    private List<OrderStatisticsOptionResponse> historicalDepartments(
            OrderStatisticsAccessScope scope,
            String keyword
    ) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = orderConditions(scope, parameters);
        conditions.add("ci.department_id IS NOT NULL");
        if (hasText(keyword)) {
            conditions.add("LOWER(COALESCE(d.name, ci.department)) LIKE :keyword");
            parameters.put("keyword", "%" + normalizeKeyword(keyword) + "%");
        }
        String sql = """
                SELECT
                    ci.department_id,
                    MAX(COALESCE(d.name, ci.department)),
                    MAX(d.parent_id),
                    MAX(d.status)
                FROM t_orders o
                LEFT JOIN t_order_context_infos ci ON ci.id = o.context_info_id
                LEFT JOIN t_system_users owner_user ON owner_user.id = o.user_id
                LEFT JOIN t_department d ON d.id = ci.department_id
                WHERE %s
                GROUP BY ci.department_id
                ORDER BY MAX(COALESCE(d.name, ci.department)), ci.department_id
                LIMIT 500
                """.formatted(String.join("\n  AND ", conditions));
        return execute(sql, parameters).stream()
                .map(row -> OrderStatisticsOptionResponse.builder()
                        .id(stringId(row[0]))
                        .name(stringValue(row[1]))
                        .parentId(stringId(row[2]))
                        .status(stringValue(row[3]))
                        .historical(true)
                        .disabled(true)
                        .build())
                .toList();
    }

    private List<String> orderConditions(
            OrderStatisticsAccessScope scope,
            Map<String, Object> parameters
    ) {
        List<String> conditions = new ArrayList<>();
        conditions.add("o.company_id = :companyId");
        conditions.add("o.status <> 'DELETED'");
        parameters.put("companyId", scope.companyId());
        if (scope.personalOnly()) {
            conditions.add("(ci.sales_uid = :requesterUserId OR o.user_id = :requesterUserId)");
            parameters.put("requesterUserId", scope.requesterUserId());
        } else if (!scope.companyWide()) {
            String departmentExpression = "COALESCE(ci.department_id, owner_user.department_id)";
            addDepartmentSetCondition(conditions, parameters, scope, departmentExpression);
        }
        if (scope.websiteScoped()) {
            conditions.add("ci.website_id = :websiteId");
            parameters.put("websiteId", scope.websiteId());
        }
        return conditions;
    }

    private void addCurrentDepartmentPermission(
            List<String> conditions,
            Map<String, Object> parameters,
            OrderStatisticsAccessScope scope,
            String departmentExpression,
            String userExpression
    ) {
        if (scope.personalOnly()) {
            if (userExpression == null) {
                conditions.add("1 = 0");
            } else {
                conditions.add(userExpression + " = :requesterUserId");
                parameters.put("requesterUserId", scope.requesterUserId());
            }
            return;
        }
        if (!scope.companyWide()) {
            addDepartmentSetCondition(conditions, parameters, scope, departmentExpression);
        }
    }

    private void addDepartmentSetCondition(
            List<String> conditions,
            Map<String, Object> parameters,
            OrderStatisticsAccessScope scope,
            String expression
    ) {
        if (!scope.excludedDepartmentIds().isEmpty()) {
            conditions.add(expression + " IS NOT NULL");
            conditions.add(expression + " NOT IN ("
                    + addParameters(parameters, "excludedDepartment", scope.excludedDepartmentIds())
                    + ")");
        } else if (!scope.allowedDepartmentIds().isEmpty()) {
            conditions.add(expression + " IN ("
                    + addParameters(parameters, "allowedDepartment", scope.allowedDepartmentIds())
                    + ")");
        } else {
            conditions.add("1 = 0");
        }
    }

    private String addParameters(
            Map<String, Object> parameters,
            String prefix,
            Collection<Long> values
    ) {
        List<String> names = new ArrayList<>();
        int index = 0;
        for (Long value : values) {
            String name = prefix + index++;
            parameters.put(name, value);
            names.add(":" + name);
        }
        return String.join(", ", names);
    }

    private List<Object[]> execute(String sql, Map<String, Object> parameters) {
        Query query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows;
    }

    private List<Object> executeScalar(String sql, Map<String, Object> parameters) {
        Query query = entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        return rows;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeKeyword(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String stringId(Object value) {
        return value == null ? null : String.valueOf(((Number) value).longValue());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean result) {
            return result;
        }
        return ((Number) value).intValue() != 0;
    }
}
