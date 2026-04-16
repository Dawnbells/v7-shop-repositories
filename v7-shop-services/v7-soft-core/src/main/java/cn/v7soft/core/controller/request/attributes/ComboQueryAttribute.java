package cn.v7soft.core.controller.request.attributes;

import cn.v7soft.core.controller.request.QueryPageRequest;
import java.util.function.Function;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class ComboQueryAttribute<T> implements QueryAttribute {
    protected final QueryPageRequest<T> queryPageRequest;
    protected final List<QueryAttribute> queryAttributes = new ArrayList<>();

    protected ComboQueryAttribute(QueryPageRequest<T> queryPageRequest) {
        this.queryPageRequest = queryPageRequest;
    }

    protected abstract Predicate toPredicate(CriteriaBuilder criteriaBuilder, Predicate[] predicates);

    public <M> ComboQueryAttribute<T> equals(String name, M value) {
        this.queryAttributes.add(EqualsQueryAttribute.builder().name(name).value(value).build());
        return this;
    }
    public <M> ComboQueryAttribute<T> not(String name, M value) {
        this.queryAttributes.add(NotQueryAttribute.builder().name(name).value(value).build());
        return this;
    }

    public ComboQueryAttribute<T> add(QueryAttribute queryAttribute) {
        this.queryAttributes.add(queryAttribute);
        return this;
    }

    public ComboQueryAttribute<T> addConstraint(boolean constraint, Function<T, ? extends QueryAttribute> function) {
        if (constraint) {
            this.queryAttributes.add(function.apply(null));
        }
        return this;
    }
    public ComboQueryAttribute<T> addConstraint(boolean constraint, QueryAttribute queryAttribute) {
        if (constraint) {
            this.queryAttributes.add(queryAttribute);
        }
        return this;
    }

    public ComboQueryAttribute<T> remove(int index) {
        this.queryAttributes.remove(index);
        return this;
    }

    public ComboQueryAttribute<T> remove(QueryAttribute attribute) {
        this.queryAttributes.remove(attribute);
        return this;
    }

    public ComboQueryAttribute<T> clear() {
        this.queryAttributes.clear();
        return this;
    }

    public QueryPageRequest<T> next() {
        queryPageRequest.add(this);
        return queryPageRequest;
    }


    public AndQueryAttribute<T> and() {
        return AndQueryAttribute.create(queryPageRequest);
    }


    public OrQueryAttribute or() {
        return OrQueryAttribute.create(queryPageRequest);
    }

    public boolean isEmpty() {
        return queryAttributes.isEmpty();
    }

    @Override
    public <T> Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (queryAttributes.isEmpty()) {
            return null;
        }
        List<Predicate> predicates = new ArrayList<>();
        for (QueryAttribute queryAttribute : queryAttributes) {
            Predicate predicate = queryAttribute.toPredicate(root, query, criteriaBuilder);
            if (predicate != null) {
                predicates.add(predicate);
            }
        }
        return toPredicate(criteriaBuilder, toTypeArray(predicates));
    }


    private Predicate[] toTypeArray(List<Predicate> queryAttributes) {
        Predicate[] predicates = new Predicate[queryAttributes.size()];
        queryAttributes.toArray(predicates);
        return predicates;
    }
}
