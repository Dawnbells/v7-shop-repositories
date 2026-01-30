package cn.v7soft.accountservice.service;

import cn.v7soft.accountservice.controller.req.LoginRequest;
import cn.v7soft.accountservice.controller.req.SystemUserAddRequest;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.entities.primary.SystemUser;

public interface ISystemUserService extends IBaseService<SystemUser> {
    /**
     * 账号密码登录
     * @param request 登录请求
     * @return token
     */
    String login(LoginRequest request);

    /**
     * 检查用户名和电话号码是否已存在
     *
     * @param request 请求
     * @return
     */
    SystemUser addUser(SystemUserAddRequest request);

    /**
     * 获取有效期为60秒且一次性的登录信息Ticket
     */
    String getTicket();

    /**
     * 根据ticket进行登录
     * @param ticket ticket
     */
    String loginByTicket(String ticket);
}
