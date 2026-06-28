package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class OrderStatisticsQueryRepository {

    private final EntityManager entityManager;
    private final OrderStatisticsSqlBuilder sqlBuilder;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderStatisticsQueryRepository(EntityManager entityManager) {
        this(entityManager, new OrderStatisticsSqlBuilder());
    }

    OrderStatisticsQueryRepository(
            EntityManager entityManager,
            OrderStatisticsSqlBuilder sqlBuilder
    ) {
        this.entityManager = entityManager;
        this.sqlBuilder = sqlBuilder;
    }

    public List<OrderStatisticsAggregateRow> query(
            List<OrderStatisticsBucket> buckets,
            OrderStatisticsQueryCriteria criteria,
            OrderStatisticsAccessScope scope
    ) {
        OrderStatisticsSqlPlan plan = sqlBuilder.build(buckets, criteria, scope);
        Query query = entityManager.createNativeQuery(plan.sql());
        plan.parameters().forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::convertRow).toList();
    }

    private OrderStatisticsAggregateRow convertRow(Object[] row) {
        return new OrderStatisticsAggregateRow(
                String.valueOf(row[0]),
                toLong(row[1]),
                row[2] == null ? null : String.valueOf(row[2]),
                row[3] == null ? null : String.valueOf(row[3]),
                toBigDecimal(row[4]),
                OrderStatus.valueOf(String.valueOf(row[5])),
                ((Number) row[6]).longValue(),
                toBigDecimal(row[7])
        );
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
