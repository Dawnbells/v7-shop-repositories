package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
public class InAttribute<M> extends OperationalQueryAttribute<List<M>> {

    @Override
    @SuppressWarnings("unchecked")
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        String[] split = name.split("\\.");
        Path<?> path = root;
        for (String attributeName: split) {
            path = path.get(attributeName);
        }
        if (value == null) {
            return criteriaBuilder.isNull(path);
        }
        CriteriaBuilder.In<M> in = criteriaBuilder.in((Expression<? extends M>) path);
        for (M m: value) {
            in.value(m);
        }
        return in;
    }
}
