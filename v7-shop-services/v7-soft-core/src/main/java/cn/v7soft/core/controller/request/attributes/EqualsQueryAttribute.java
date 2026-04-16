package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class EqualsQueryAttribute<M> extends OperationalQueryAttribute<M> {

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        Path<?> path = getPathFromName(root);
        if (value == null) {
            return criteriaBuilder.isNull(path);
        }
        return criteriaBuilder.equal(path, value);
    }
}
