package cn.v7soft.core.controller.request.attributes;

import cn.v7soft.core.controller.request.QueryPageRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

/**
 * 或操作
 */
public final class OrQueryAttribute<T> extends ComboQueryAttribute<T> {
    private OrQueryAttribute(QueryPageRequest<T> queryPageRequest) {
        super(queryPageRequest);
    }

    @Override
    protected Predicate toPredicate(CriteriaBuilder criteriaBuilder, Predicate[] predicates) {
        return criteriaBuilder.or(predicates);
    }

    public static <T> OrQueryAttribute<T> create(QueryPageRequest<T> request) {
        return new OrQueryAttribute<>(request);
    }
}
