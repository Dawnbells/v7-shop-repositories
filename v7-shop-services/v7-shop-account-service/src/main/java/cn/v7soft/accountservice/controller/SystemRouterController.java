package cn.v7soft.accountservice.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.accountservice.controller.req.TreeSystemRouterRequest;
import cn.v7soft.accountservice.controller.resp.SystemRouterResponse;
import cn.v7soft.accountservice.service.ISystemRouterService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.enums.SystemRouterType;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.tenant.WebsiteContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.Hibernate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Validated
@RestController
@RequestMapping("/router")
@Tag(name = "账户中心/菜单路由")
public class SystemRouterController {
    private final ISystemRouterService systemRouterService;

    public SystemRouterController(ISystemRouterService systemRouterService) {
        this.systemRouterService = systemRouterService;
    }

    @SaCheckLogin
    @GetMapping("/getList")
    @Operation(summary = "获取路由")
    public List<SystemRouterResponse> getList() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        if (loginUser.getUserType() == SystemUserType.ADMIN) {
            List<SystemRouter> topSystemRouters = systemRouterService.treeAllTopSystemRouters(null, WebsiteContext.getCurrentPlatform());
            return topSystemRouters.stream().map(systemRouter -> {
                SystemRouterResponse systemRouterResponse = convertEntityCopyId(systemRouter);
                systemRouterResponse.setChildren(deepConvertChildren(systemRouter, null, SystemRouterType.MENU));
                systemRouterResponse.setParentId(systemRouter.getParent() == null ? null : systemRouter.getParent().getId());
                return systemRouterResponse;
            }).toList();
        }
        List<SystemRouter> routers = systemRouterService.treeAllSystemRoutersForCurrentUser(WebsiteContext.getCurrentPlatform());
        return treeRouter(routers);
    }

    @SaCheckLogin
    @PostMapping("/getRouterList")
    @Operation(summary = "获取所有路由列表")
    public List<SystemRouterResponse> getRouterList() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        List<SystemRouter> topSystemRouters;
        if (loginUser.getUserType() == SystemUserType.ADMIN) {
            topSystemRouters = systemRouterService.treeAllTopSystemRouters(null, null);
        } else {
            topSystemRouters = systemRouterService.treeAllTopSystemRouters(StatusEnum.VALID, null);
        }
        return topSystemRouters.stream().map(systemRouter -> {
            SystemRouterResponse systemRouterResponse = convertEntityCopyId(systemRouter);
            systemRouterResponse.setChildren(deepConvertChildren(systemRouter, null, null));
            systemRouterResponse.setParentId(systemRouter.getParent() == null ? null : systemRouter.getParent().getId());
            return systemRouterResponse;
        }).toList();
    }

    @SaCheckLogin
    @GetMapping("/tree")
    @Operation(summary = "获取路由")
    public List<SystemRouterResponse> getTree(@Valid @RequestBody TreeSystemRouterRequest request) {
        StatusEnum statusEnum = request == null ? null : request.getStatus();
        List<SystemRouter> topSystemRouters = systemRouterService.treeAllTopSystemRouters(statusEnum, null);
        return topSystemRouters.stream().map(systemRouter -> {
            SystemRouterResponse systemRouterResponse = convertEntityCopyId(systemRouter);
            systemRouterResponse.setChildren(deepConvertChildren(systemRouter, statusEnum, null));
            systemRouterResponse.setParentId(systemRouter.getParent() == null ? null : systemRouter.getParent().getId());
            return systemRouterResponse;
        }).toList();
    }

    private List<SystemRouterResponse> deepConvertChildren(SystemRouter systemRouter, StatusEnum status, SystemRouterType type) {
        if (systemRouter.getChildren() == null || systemRouter.getChildren().isEmpty()) {
            return null;
        }
        return systemRouter.getChildren().stream()
                .filter(r -> (status == null || r.getStatus() == status) && (type == null || r.getType() == type))
                .map(d -> {
                    SystemRouterResponse systemRouterResponse = convertEntityCopyId(d);
                    systemRouterResponse.setChildren(deepConvertChildren(d, status, type));
                    systemRouterResponse.setParentId(d.getParent() != null ? d.getParent().getId() : null);
                    return systemRouterResponse;
                }).toList();
    }

    private SystemRouterResponse convertEntityCopyId(SystemRouter systemRouter) {
        return SystemRouterResponse.builder()
                .id(systemRouter.getId())
                .path(systemRouter.getPath())
                .name(systemRouter.getName())
                .component(systemRouter.getComponent())
                .meta(systemRouter.getMeta())
                .type(systemRouter.getType())
                .platform(systemRouter.getPlatform())
                .build();
    }


    private List<SystemRouterResponse> treeRouter(List<SystemRouter> routers) {
        if (routers == null || routers.isEmpty()) {
            return new ArrayList<>();
        }
        routers.forEach(systemRouter -> {
            Hibernate.unproxy(systemRouter);
            systemRouter.setChildren(null);
        });
        Stream<SystemRouter> topRouters = routers.stream().filter(router -> router.getParent() == null);
        return topRouters.map(systemRouter -> {
            systemRouter.setChildren(deepRouterChildren(systemRouter, routers));
            return SystemRouterResponse.fromSystemRouter(systemRouter, null);
        }).collect(Collectors.toList());
    }

    private List<SystemRouter> deepRouterChildren(SystemRouter parentRouter, List<SystemRouter> routers) {
        List<SystemRouter> children = new ArrayList<>();
        routers.forEach(systemRouter -> {
            if (systemRouter.getParent() != null && Objects.equals(systemRouter.getParent().getId(), parentRouter.getId())) {
                if (systemRouter.getChildren() != null) {
                    ServiceResponseEnum.ERR_ROUTER_TREE.throwException(systemRouter.getId().toString());
                }
                systemRouter.setChildren(deepRouterChildren(systemRouter, routers));
                children.add(systemRouter);
            }
        });
        return children;
    }
}
