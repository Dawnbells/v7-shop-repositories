package cn.v7soft.admin.statistics;

import java.util.Map;

public record OrderStatisticsSqlPlan(
        String sql,
        Map<String, Object> parameters
) {
}
