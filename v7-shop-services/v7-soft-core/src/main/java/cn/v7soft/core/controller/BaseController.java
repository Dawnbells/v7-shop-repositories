package cn.v7soft.core.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base62;
import cn.v7soft.core.controller.request.*;
import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.core.entities.IBaseEntity;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.IBaseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller CRUD基类
 *
 * @param <T> Entity实体类
 * @param <S> Service接口类
 * @param <R> 实体响应类
 * @param <Q> 分页请求类
 * @param <E> 新增/编辑请求类
 */
public abstract class BaseController<T extends IBaseEntity, S extends IBaseService<T>, R extends IdResponse, Q extends BasePageRequest, E extends IdRequest> {
    protected final S service;

    protected BaseController(S service) {
        this.service = service;
    }

    /**
     * Entity转换成Response对象
     *
     * @param t entity对象
     * @return Response对象
     */
    protected abstract R convertEntity(T t);

    /**
     * Request转换为Entity对象
     *
     * @param dbEntity 数据库实体, 如果是编辑，则不为空，如果为空，则为新增，需要新建
     * @param request  Request对象
     * @return Entity对象
     */
    protected abstract T convertRequest(@Nullable T dbEntity, E request);

    /**
     * 检查的权限前缀，其中分页，添加, 更新，删除，切换状态分别加上以下后缀：
     * prefix.page
     * prefix.create
     * prefix.update
     * prefix.delete
     * prefix.switch
     *
     * @return 权限前缀
     */
    protected abstract String getPermissionPrefix();

    /**
     * 构造复杂分页查询
     *
     * @param request 分页查询请求
     * @return 复杂分页查询请求结构体，默认仅为简单的分页查询
     */
    protected QueryPageRequest<T> convertQueryPageRequest(Q request) {
        return QueryPageRequest.fromRequest(request);
    }


    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<R> page(@Valid @RequestBody Q request) {
        String permission = getPermissionPrefix() + ".page";
        StpUtil.checkPermission(permission);
        return service.findPaginated(convertQueryPageRequest(request))
                .map(this::convertEntityCopyId);
    }


    @PostMapping("/doEdit")
    @Operation(summary = "更新或编辑")
    public R doEdit(@Valid @RequestBody E request) {
        String permission = getPermissionPrefix() + (request.getId() == null ? ".create" : ".update");
        StpUtil.checkPermission(permission);
        validRequest(request);
        T t = doEditOperate(request);
        return convertEntityCopyId(t);
    }


    @PostMapping("/doDelete")
    @Operation(summary = "根据ID删除")
    public void doDelete(@Valid @RequestBody DeleteRequest request) {
        String permission = getPermissionPrefix() + ".delete";
        StpUtil.checkPermission(permission);
        if (!cleanupBeforeDelete(request)) {
            ServiceResponseEnum.UNSUPPORTED.throwException();
        }
        List<Long> ids = null;
        try {
            ids = Arrays.stream(request.getIds().split(",")).map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("IDS参数错: " + request.getIds());
        }
        ClientResponseEnum.PARAMETER_ILLEGAL.notEmpty(ids, "IDS参数为空");
        service.deleteAll(ids);
    }

    @PostMapping("/switchValidity")
    @Operation(summary = "根据ID切换是否有效")
    public void switchValidity(@Valid @RequestBody SwitchValidityRequest request) {
        String permission = getPermissionPrefix() + ".switch";
        StpUtil.checkPermission(permission);
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(request.getId(), "ID参数为空");
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(request.getStatus() != StatusEnum.DELETED,
                "不允许DELETED状态");
        assert request.getId() != null;
        service.switchStatus(Long.parseLong(request.getId()), request.getStatus());
    }

    protected R convertEntityCopyId(T t) {
        R r = convertEntity(t);
        return filling(t, r);
    }

    protected <M extends IBaseEntity, N extends IdResponse> N filling(M t, N r) {
        r.setId(String.valueOf(t.getId()));
        r.setCompactId(Base62.encode(String.valueOf(t.getId())));
        r.setStatus(t.getStatus());
        return r;
    }

    protected T doEditOperate(E request) {
        T entity = null;
        if (StringUtils.hasText(request.getId())) {
            long id = Long.parseLong(request.getId());
            T t = service.getById(id);
            ClientResponseEnum.PARAMETER_ILLEGAL.notNull(t, "传入的ID有误");
            entity = t;
        }
        T t = fillEntity(convertRequest(entity, request));
        return service.save(t);
    }

    protected T fillEntity(T t) {
        return t;
    }

    protected void validRequest(E request) {

    }

    protected boolean cleanupBeforeDelete(DeleteRequest request) {
        return false;
    }
}
