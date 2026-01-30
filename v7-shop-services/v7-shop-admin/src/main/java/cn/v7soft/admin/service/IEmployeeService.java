package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.DispatchDepartmentRequest;
import cn.v7soft.admin.controller.req.GrantRoleRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Role;
import cn.v7soft.dao.entities.primary.SystemUser;

public interface IEmployeeService extends IBaseDataRangeService<SystemUser> {
    /**
     * 授予角色
     * @param request 请求
     */
    void doGrantRole(GrantRoleRequest request);

    /**
     * 给员工分配部门
     * @param request 请求
     */
    void dispatchDepartment(DispatchDepartmentRequest request);

    /**
     * 根据角色重新变更用户数据权限范围
     *
     * @param role 角色
     * @return
     */
    int changeUserTypeWithRole(Role role);
}
