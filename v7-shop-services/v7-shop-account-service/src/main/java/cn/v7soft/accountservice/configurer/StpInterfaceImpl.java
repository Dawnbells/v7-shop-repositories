package cn.v7soft.accountservice.configurer;

import cn.dev33.satoken.stp.StpInterface;
import cn.v7soft.accountservice.service.ISystemRouterService;
import cn.v7soft.accountservice.service.ISystemUserService;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {
    private final ISystemUserService systemUserService;
    private final ISystemRouterService systemRouterService;

    public StpInterfaceImpl(ISystemUserService systemUserService, ISystemRouterService systemRouterService) {
        this.systemUserService = systemUserService;
        this.systemRouterService = systemRouterService;
    }

    @Override
    @Transactional
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long loggingUserId = Long.parseLong(String.valueOf(loginId));
        SystemUser loggedUser = this.systemUserService.getById(loggingUserId);
        if (loggedUser.getUserType() == SystemUserType.ADMIN || loggedUser.getUserType() == SystemUserType.COMPANY_ADMIN) {
            return List.of("*");
        }
        return systemRouterService.getPermissionList(loggingUserId);
    }

    @Override
    @Transactional
    public List<String> getRoleList(Object loginId, String loginType) {
        Long loggingUserId = Long.parseLong(String.valueOf(loginId));
        return this.systemUserService.getById(loggingUserId).getRoles().stream().map(Role::getName).collect(Collectors.toList());
    }
}
