package cn.v7soft.common.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.core.controller.BaseController;
import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.entities.base.IBaseDataRangeEntity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

public abstract class BaseDataRangeController<T extends IBaseDataRangeEntity, S extends IBaseDataRangeService<T>, R extends IdResponse, Q extends BasePageRequest, E extends IdRequest>
        extends BaseController<T, S, R, Q, E> {

    protected BaseDataRangeController(S service) {
        super(service);
    }

    @Override
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<R> page(@Valid @RequestBody Q request) {
        String permission = getPermissionPrefix() + ".page";
        StpUtil.checkPermission(permission);
        AccessDataRangeLevel level = getPageAccessDataRangeLevel(request);
        if (level != null) {
            return service.findPaginated(convertQueryPageRequest(request), level)
                    .map(this::convertEntityCopyId);
        }
        return service.findPaginated(convertQueryPageRequest(request))
                .map(this::convertEntityCopyId);
    }

    @Override
    protected T fillEntity(T t) {
        if (t instanceof BaseDataRangeEntity) {
            return (T) ((BaseDataRangeEntity) t).fillOwner();
        }
        if (t instanceof BaseAutoIdDataRangeEntity) {
            return (T) ((BaseAutoIdDataRangeEntity) t).fillOwner();
        }
        return t;
    }

    protected AccessDataRangeLevel getPageAccessDataRangeLevel(Q request) {
        return null;
    }
}
