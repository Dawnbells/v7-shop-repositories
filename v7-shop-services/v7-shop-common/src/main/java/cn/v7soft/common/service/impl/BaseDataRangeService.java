package cn.v7soft.common.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.OrQueryAttribute;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.base.IBaseDataRangeEntity;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.ViewMode;

/**
 * 控制数据访问权限
 *
 * @param <T>
 * @param <M>
 */
public abstract class BaseDataRangeService<T extends IBaseDataRangeEntity, M extends BaseRepository<T>> extends BaseService<T, M> implements IBaseDataRangeService<T> {

    public BaseDataRangeService(M repository) {
        super(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request) {
        OrQueryAttribute<T> or = request.or();
        or.add(getAccessDataRangeQueryAttribute());
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return super.findPaginated(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request, AccessDataRangeLevel level) {
        OrQueryAttribute<T> or = request.or();
        or.add(new AccessDataRangeAttribute(level));
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return super.findPaginated(request);
    }

    /**
     * 设置某些条件下忽略数据权限
     *
     * @param or 忽略数据权限的条件
     */
    protected void addIgnoreAccessDataRageCondition(OrQueryAttribute<T> or) {
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request, SystemUser systemUser) {
        OrQueryAttribute<T> or = request.or();
        or.add(new AccessDataRangeAttribute().setOwner(systemUser));
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return super.findPaginated(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request, SystemUser systemUser, ViewMode viewMode) {
        SystemUserDto systemUserDto = SystemUserDto.convert(systemUser);
        return findPaginated(request, systemUserDto, viewMode);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request, SystemUserDto systemUser) {
        OrQueryAttribute<T> or = request.or();
        or.add(new AccessDataRangeAttribute().setOwner(systemUser));
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return super.findPaginated(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findPaginated(QueryPageRequest<T> request, SystemUserDto systemUser, ViewMode viewMode) {
        OrQueryAttribute<T> or = request.or();
        if (Boolean.TRUE.equals(systemUser.getIsCrossDepartment())) {
            or.add(new AccessDataRangeAttribute(AccessDataRangeLevel.SPECIFIED_DEPARTMENTS, systemUser.getManageDepartmentIds()).setOwner(systemUser).setViewMode(viewMode));
        } else {
            or.add(new AccessDataRangeAttribute().setOwner(systemUser).setViewMode(viewMode));
        }
        addIgnoreAccessDataRageCondition(or);
        or.next();
        return super.findPaginated(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<T> findOriginalPaginated(QueryPageRequest<T> request) {
        return super.findPaginated(request);
    }

    @Override
    @Transactional
    public T save(T data) {
        if (data instanceof BaseDataRangeEntity) {
            super.save((T) ((BaseDataRangeEntity) data).fillOwner());
        }
        if (data instanceof BaseAutoIdDataRangeEntity) {
            super.save((T) ((BaseAutoIdDataRangeEntity) data).fillOwner());
        }
        return super.save(data);
    }

    @Override
    @Transactional
    public T saveAndFlush(T data) {
        if (data instanceof BaseDataRangeEntity) {
            super.saveAndFlush((T) ((BaseDataRangeEntity) data).fillOwner());
        }
        if (data instanceof BaseAutoIdDataRangeEntity) {
            super.saveAndFlush((T) ((BaseAutoIdDataRangeEntity) data).fillOwner());
        }
        return super.saveAndFlush(data);
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute();
    }

}

