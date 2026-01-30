package cn.v7soft.admin.service;

import cn.v7soft.dao.entities.primary.Order;

/**
 * 邮件服务接口
 */
public interface IEmailService {

    /**
     * 发送订单确认邮件给客户
     *
     * @param order 订单信息
     */
    void sendOrderConfirmationEmail(Order order);
}

