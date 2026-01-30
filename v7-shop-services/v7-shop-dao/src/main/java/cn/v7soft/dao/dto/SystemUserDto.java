package cn.v7soft.dao.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.Gender;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.enums.ViewMode;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class SystemUserDto extends IdDto {
    private Long companyId;
    /**
     * 名字
     */
    private String name;
    /**
     * 性别
     */
    private Gender gender;
    /**
     * 电话
     */
    private String telephone;
    /**
     * 用户类型
     */
    private SystemUserType userType;
    /**
     * 所属部门ID
     */
    private Long departmentId;
    /**
     * 所属部门名称
     */
    private String departmentName;
    /**
     * 深度可访问部门
     */
    private List<Long> accessDepartmentIds;
    /**
     * 父级部门树
     */
    private List<Long> parentDepartmentIds;
    /**
     * 可以给别人赋予哪些角色
     */
    private List<Long> assignableRoleIds;
    /**
     * 是否支持审单
     */
    private Boolean isAuditOrders;

    public static SystemUserDto convert(SystemUser user) {
        Department department = user.getDepartment();

        List<Long> accessDepartmentIds = new ArrayList<>();
        List<Long> parentDepartmentIds = new ArrayList<>();
        Long departmentId = null;
        String departmentName = "";
        if (department != null) {
            departmentId = department.getId();
            departmentName = department.getName();
            accessDepartmentIds.add(departmentId);
            deepCollectAccessDepartmentIds(department, accessDepartmentIds);
            deepCollectParentDepartmentIds(department, parentDepartmentIds);
        }

        boolean isAuditOrders = user.getRoles()
                .stream()
                .anyMatch(role -> Boolean.TRUE.equals(role.getIsAuditOrders()));

        List<Long> assignableRoles = user.getRoles().stream()
                .flatMap(role -> role.getAssignableRoles().stream()) // 扁平化角色的 assignableRoles
                .map(Role::getId)
                .toList(); // 收集成一个 List<Role>

        return SystemUserDto.builder()
                .id(String.valueOf(user.getId()))
                .companyId(user.getCompanyId())
                .name(user.getName())
                .gender(user.getGender())
                .telephone(user.getTelephone())
                .userType(user.getUserType())
                .departmentId(departmentId)
                .departmentName(departmentName)
                .accessDepartmentIds(accessDepartmentIds)
                .parentDepartmentIds(parentDepartmentIds)
                .assignableRoleIds(assignableRoles)
                .isAuditOrders(isAuditOrders)
                .build();
    }

    private static void deepCollectParentDepartmentIds(Department department, List<Long> parentDepartmentIds) {
        Department parent = department.getParent();
        if (parent == null) {
            return;
        }
        parentDepartmentIds.add(parent.getId());
        deepCollectParentDepartmentIds(parent, parentDepartmentIds);
    }

    private static void deepCollectAccessDepartmentIds(Department department, List<Long> accessDepartmentIds) {
        List<Department> children = department.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Department child : children) {
            accessDepartmentIds.add(child.getId());
            deepCollectAccessDepartmentIds(child, accessDepartmentIds);
        }
    }

    public SystemUser toOwner() {
        return SystemUser.builder().id(getLongId()).build();
    }

    public boolean isAdmin() {
        return userType == SystemUserType.ADMIN || userType == SystemUserType.COMPANY_ADMIN;
    }

    public boolean isDepartmentManager() {
        return userType == SystemUserType.DEPARTMENT_MANAGER || userType == SystemUserType.DEEP_DEPARTMENT_MANAGER;
    }

    public boolean isDeepDepartmentManager() {
        return userType == SystemUserType.DEEP_DEPARTMENT_MANAGER;
    }

    public boolean isSuperAdmin() {
        return userType == SystemUserType.ADMIN;
    }

    public boolean hasManagerPermission(Long userId, Long departmentId) {
        if (isAdmin() || Objects.equals(userId, getLongId())) {
            // 管理员和自己可以管理自己
            return true;
        }
        if (isDepartmentManager() && Objects.equals(departmentId, this.departmentId)) {
            // 部门管理员可以管理同一部门的人
            return true;
        }
        if (userType == SystemUserType.DEEP_DEPARTMENT_MANAGER) {
            // 深度部门管理员可以管理同一部门和子部门的人
            return accessDepartmentIds.contains(departmentId);
        }
        if (userType == SystemUserType.DEPARTMENT_TREE) {
            // 部门树
            return parentDepartmentIds.contains(departmentId) || accessDepartmentIds.contains(departmentId);
        }
        // 普通用户不可以管理非本人
        return false;
    }
}
