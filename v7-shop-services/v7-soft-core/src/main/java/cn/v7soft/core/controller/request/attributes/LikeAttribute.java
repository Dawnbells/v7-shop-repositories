package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class LikeAttribute extends OperationalQueryAttribute<String> {
    @Builder.Default
    private boolean leftMatch = true;
    @Builder.Default
    private boolean rightMatch = true;

    @Override
    @SuppressWarnings("unchecked")
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        Path<?> path = getPathFromName(root);
        if (value.startsWith("%")) {
            leftMatch = false;
        }
        if (value.endsWith("%")) {
            rightMatch = false;
        }
        return criteriaBuilder.like(criteriaBuilder.toString((Expression<Character>) path), (leftMatch ? "%" : "") + value + (rightMatch ? "%" : ""));
    }
}
