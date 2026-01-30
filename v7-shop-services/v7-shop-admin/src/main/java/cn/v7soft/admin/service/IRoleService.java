package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.GrantRoutersRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Role;

import java.util.List;

public interface IRoleService extends IBaseDataRangeService<Role> {
    /**
     * 获取所有角色
     *
     * @return 返回所有角色列表
     */
    List<Role> getAllValid();

    /**
     * 分配路由
     *
     * @param request
     */
    void grantRouters(GrantRoutersRequest request);
}
