package cn.v7soft.core.controller.request;

import java.util.function.Function;

import cn.v7soft.core.controller.request.attributes.AndQueryAttribute;
import cn.v7soft.core.controller.request.attributes.ComboQueryAttribute;
import cn.v7soft.core.controller.request.attributes.OrQueryAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;

public final class QueryPageRequest<T> {
    private final BasePageRequest request;
    private final AndQueryAttribute<T> attributes = AndQueryAttribute.create(this);

    private QueryPageRequest(BasePageRequest request) {
        this.request = request;
    }

    public Pageable toPageable() {
        return request.toPageable();
    }


    public QueryPageRequest<T> add(QueryAttribute queryAttribute) {
        this.attributes.add(queryAttribute);
        return this;
    }
    public QueryPageRequest<T> addConstraint(boolean constraint, QueryAttribute queryAttribute) {
        if (constraint) {
            this.attributes.add(queryAttribute);
        }
        return this;
    }

    public QueryPageRequest<T> addConstraint(boolean constraint, Function<T, ? extends QueryAttribute> function) {
        if (constraint) {
            this.attributes.add(function.apply(null));
        }
        return this;
    }

    public QueryPageRequest<T> remove(int index) {
        this.attributes.remove(index);
        return this;
    }

    public QueryPageRequest<T> remove(QueryAttribute attribute) {
        this.attributes.remove(attribute);
        return this;
    }

    public QueryPageRequest<T> clear() {
        this.attributes.clear();
        return this;
    }

    public OrQueryAttribute<T> or() {
        return OrQueryAttribute.create(this);
    }

    public <M> QueryPageRequest<T> equals(String name, M value) {
        this.attributes.equals(name, value);
        return this;
    }
    public <M> QueryPageRequest<T> not(String name, M value) {
        this.attributes.not(name, value);
        return this;
    }

    public QueryPageRequest<T> isNull(String name) {
        return equals(name, null);
    }

    public boolean isEmpty() {
        return this.attributes.isEmpty();
    }


    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (isEmpty()) {
            return null;
        }
        return this.attributes.toPredicate(root, query, criteriaBuilder);
    }

    public static <T> QueryPageRequest<T> fromRequest(BasePageRequest request) {
        return new QueryPageRequest<>(request);
    }

    public static <T> QueryPageRequest<T> fromEmpty() {
        return new QueryPageRequest<>( BasePageRequest.builder().build());
    }
    public static <T> QueryPageRequest<T> fromUnLimit() {
        return new QueryPageRequest<>( BasePageRequest.builder().pageSize(Integer.MAX_VALUE).build());
    }
}
