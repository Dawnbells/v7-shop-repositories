package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class NotQueryAttribute<M> extends OperationalQueryAttribute<M> {

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.notEqual(root.<M>get(name), value);
    }
}
