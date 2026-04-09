package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.DispatchDepartmentRequest;
import cn.v7soft.admin.controller.req.GrantRoleRequest;
import cn.v7soft.admin.service.IEmployeeService;
import cn.v7soft.common.controller.req.attributes.SystemUserAccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeService extends BaseDataRangeService<SystemUser, SystemUserRepository> implements IEmployeeService {
    public EmployeeService(SystemUserRepository systemUserRepository) {
        super(systemUserRepository);
    }

    @Override
    protected void checkKeyConstraint(SystemUser data) {
        SystemUser sameUser = repository.findBySameUser(data.getTelephone(), data.getId());
        ClientResponseEnum.PARAMETER_ILLEGAL.isNull(sameUser, "手机号不允许重复");
    }

    @Override
    @Transactional
    public void doGrantRole(GrantRoleRequest request) {
        assert request.getId() != null;
        SystemUser systemUser = getById(Long.parseLong(request.getId()));
        List<Role> roles = request.getRoleIds().stream().map(roleId -> Role.builder().id(roleId).build()).distinct().collect(Collectors.toList());
        systemUser.setRoles(roles);
        SystemUser savedUser = save(systemUser);
        SystemUserType systemUserType = SystemUserType.EMPLOYEE;
        for (Role role : savedUser.getRoles()) {
            if (role.getUserType().getLevel() < systemUserType.getLevel()) {
                systemUserType = role.getUserType();
            }
        }
        systemUser.setUserType(systemUserType);
    }

    @Override
    @Transactional
    public void dispatchDepartment(DispatchDepartmentRequest request) {
        assert request.getId() != null;
        SystemUser systemUser = getById(Long.parseLong(request.getId()));
        systemUser.setDepartment(request.getDepartmentId() == null ? null : Department.builder().id(request.getDepartmentId()).build());
        save(systemUser);
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        if (SaSessionUtil.isCrossDepartment() && SaSessionUtil.isManageEmployee()) {
            return new SystemUserAccessDataRangeAttribute(AccessDataRangeLevel.SPECIFIED_DEPARTMENTS, SaSessionUtil.getManageDepartmentIds());
        }
        return new SystemUserAccessDataRangeAttribute();
    }


    @Override
    @Transactional
    public int changeUserTypeWithRole(Role updatedRole) {
        // 查询出所有分配了该 Role 的 SystemUser
        List<SystemUser> usersWithRole = repository.findByRolesContaining(updatedRole);
        int count = 0;
        for (SystemUser user : usersWithRole) {
            // 获取用户所有角色的 userType
            SystemUserType highestUserType = user.getRoles().stream()
                    .map(Role::getUserType) // 提取每个角色的 userType
                    .min(Comparator.comparingInt(SystemUserType::getLevel)) // 找到 level 最低的（权限最高）
                    .orElse(user.getUserType()); // 如果没有角色，保持原来的 userType

            // 更新用户的 userType
            if (user.getUserType() != highestUserType) {
                user.setUserType(highestUserType);
                repository.save(user); // 保存到数据库
                count++;
            }
        }
        return count;
    }
}
