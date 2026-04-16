package cn.v7soft.core.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.entities.IBaseEntity;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.core.service.IBaseService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;


public abstract class BaseService<T extends IBaseEntity, M extends BaseRepository<T>> implements IBaseService<T> {
    @PersistenceContext
    protected EntityManager entityManager;
    protected final M repository;
    protected final Class<T> type;

    public BaseService(M repository) {
        ResolvableType resolvableType = ResolvableType.forClass(getClass()).as(BaseService.class);
        //noinspection unchecked
        this.type = (Class<T>) resolvableType.getGeneric(0).resolve();
        this.repository = repository;
    }

    @Override
    @NotNull
    public T getById(Long id) {
        Optional<T> t = this.repository.findById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(t.isPresent(), "参数错：" + id);
        //noinspection OptionalGetWithoutIsPresent
        return t.get();
    }

    @Override
    @Transactional
    public Optional<T> findById(Long id) {
        return this.repository.findById(id);
    }

    @Override
    @Transactional
    public T save(T data) {
        checkKeyConstraint(data);
        return this.repository.save(data);
    }


    @Override
    @Transactional
    public T saveAndFlush(T data) {
        checkKeyConstraint(data);
        return this.repository.saveAndFlush(data);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(BasePageRequest request) {
        return this.findPaginated(QueryPageRequest.fromRequest(request));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request) {
        return this.repository.findAll((Specification<T>) request::toPredicate, request.toPageable());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        T t = getById(id);
        t.setStatus(StatusEnum.DELETED);
        save(t);
    }

    @Override
    @Transactional
    public void switchStatus(Long id, StatusEnum status) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        T t = getById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(t.getStatus() != StatusEnum.DELETED, "已删除");
        t.setStatus(status);
        save(t);
    }

    @Override
    @Transactional
    public void deleteAll(List<Long> ids) {
        // 构建 SQL 语句，确保 tableName 来自安全源以避免 SQL 注入
        String sql = "UPDATE " + getTableName(type) + " SET `status`='DELETED' WHERE id IN :ids";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        query.executeUpdate();
    }

    protected void checkKeyConstraint(T data) {

    }

    protected static String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Table.class)) {
            Table table = clazz.getAnnotation(Table.class);
            return table.name();  // 返回注解中的表名
        }
        return null;  // 如果没有 @Table 注解，则可能返回 null 或抛出异常
    }

}
