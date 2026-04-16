package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class GreaterThanAttribute<Y extends Comparable<? super Y>> extends OperationalQueryAttribute<Y> {
    @Builder.Default
    private final boolean equals = false;

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        Path<? extends Y> path = (Path<? extends Y>) getPathFromName(root);
        if (equals) {
            return criteriaBuilder.greaterThanOrEqualTo(path, value);
        }
        return criteriaBuilder.greaterThan(path, value);
    }
}
