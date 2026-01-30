package cn.v7soft.common.service;

import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.entities.IBaseEntity;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.ViewMode;

import org.springframework.data.domain.Page;

public interface IBaseDataRangeService<T extends IBaseEntity> extends IBaseService<T> {
    QueryAttribute getAccessDataRangeQueryAttribute();

    Page<T> findPaginated(QueryPageRequest<T> request, AccessDataRangeLevel level);

    Page<T> findPaginated(QueryPageRequest<T> request, SystemUser systemUser, ViewMode viewMode);

    Page<T> findPaginated(QueryPageRequest<T> request, SystemUserDto systemUser);

    Page<T> findPaginated(QueryPageRequest<T> request, SystemUserDto systemUser, ViewMode viewMode);

    Page<T> findOriginalPaginated(QueryPageRequest<T> request);
    Page<T> findPaginated(QueryPageRequest<T> request, SystemUser systemUser);
}
