package cn.v7soft.accountservice.service.impl;

import cn.v7soft.accountservice.configurer.SystemRouterInitializer;
import cn.v7soft.accountservice.service.ISystemRouterService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.enums.RouterPlatform;
import cn.v7soft.dao.repositories.primary.SystemRouterRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemRouterService extends BaseService<SystemRouter, SystemRouterRepository> implements ISystemRouterService {

    public SystemRouterService(SystemRouterRepository systemRouterRepository, SystemUserRepository systemUserRepository) {
        super(systemRouterRepository);
    }


    //    @PostConstruct
    public void initialize() {
        repository.deleteAll();
        repository.saveAll(SystemRouterInitializer.wrapCompanyId(SystemRouterInitializer.managerSystemRouters(), 1L));
        repository.saveAll(SystemRouterInitializer.wrapCompanyId(SystemRouterInitializer.mallSystemRouters(), 1L));
        repository.flush();
    }


    @Override
    public List<SystemRouter> treeAllSystemRoutersForCurrentUser(RouterPlatform platform) {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        return repository.getAllSystemRoutersForCurrentUser(platform, loginUser.getLongId());
    }

    @Override
    public List<SystemRouter> treeAllTopSystemRouters(StatusEnum status, RouterPlatform platform) {
        return repository.getAllTopSystemRouters(status, platform);
    }

    @Override
    public List<String> getPermissionList(Long loginId) {
        // TODO PERMISSION
        List<SystemRouter> routers = this.treeAllSystemRoutersForCurrentUser(null);
        List<SystemRouter> list = routers.stream().filter(router -> router.getId() == 130).toList();
        if (list.isEmpty()) {
            return List.of("*");
        }
        return List.of("*");
    }
}
