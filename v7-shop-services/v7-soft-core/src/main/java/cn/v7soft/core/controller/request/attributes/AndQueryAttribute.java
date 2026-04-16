package cn.v7soft.core.controller.request.attributes;

import cn.v7soft.core.controller.request.QueryPageRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

public class AndQueryAttribute<T> extends ComboQueryAttribute<T> {

    protected AndQueryAttribute(QueryPageRequest<T> queryPageRequest) {
        super(queryPageRequest);
    }

    @Override
    protected Predicate toPredicate(CriteriaBuilder criteriaBuilder, Predicate[] predicates) {
        return criteriaBuilder.and(predicates);
    }

    public static <T> AndQueryAttribute<T> create(QueryPageRequest<T> request) {
        return new AndQueryAttribute<>(request);
    }
}
