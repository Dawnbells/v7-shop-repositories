package cn.v7soft.accountservice.service;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.enums.RouterPlatform;

import java.util.List;

public interface ISystemRouterService {
    /***
     * 获取当前登录用户路由列表
     */
    List<SystemRouter> treeAllSystemRoutersForCurrentUser(RouterPlatform platform);

    List<SystemRouter> treeAllTopSystemRouters(StatusEnum status, RouterPlatform platform);

    /**
     * 获取当前用户的所有权限
     *
     * @param loginId 登录ID
     * @return 用户权限列表
     */
    List<String> getPermissionList(Long loginId);
}
