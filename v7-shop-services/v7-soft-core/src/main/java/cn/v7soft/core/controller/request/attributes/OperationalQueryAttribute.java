package cn.v7soft.core.controller.request.attributes;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class OperationalQueryAttribute<M> implements QueryAttribute {
    protected String name;
    protected M value;

    protected Path<?> getPathFromName(Root<?> root) {
        String[] split = name.split("\\.");
        Path<?> path = root;
        for (String attributeName : split) {
            path = path.get(attributeName);
        }
        return path;
    }
}
