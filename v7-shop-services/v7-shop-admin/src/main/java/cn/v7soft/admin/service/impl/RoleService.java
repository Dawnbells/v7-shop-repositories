package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.GrantRoutersRequest;
import cn.v7soft.admin.service.IRoleService;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.repositories.primary.RoleRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleService extends BaseDataRangeService<Role, RoleRepository> implements IRoleService {
    public RoleService(RoleRepository repository) {
        super(repository);
    }

    @Override
    protected void checkKeyConstraint(Role data) {
        Role role = repository.findBySameName(data.getName(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(role, "角色名不允许重复");
    }


    @Override
    @Transactional
    public void grantRouters(GrantRoutersRequest request) {
        Role role = getById(request.getIdLongValue());
        List<SystemRouter> routers = request.getRouterIds().stream().map(routerIds -> SystemRouter.builder().id(routerIds).build()).distinct()
                .collect(Collectors.toList());
        role.setSystemRouterList(routers);
        save(role);
    }

    @Override
    public List<Role> getAllValid() {
        SystemUserDto loginUser = SaSessionUtil.getLoginUser();
        List<Long> assignableRoleIds = loginUser.getAssignableRoleIds();
        return loginUser.isAdmin()? repository.findAllValidRole(): repository.listByRoleIds(assignableRoleIds);
    }

}
