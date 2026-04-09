package cn.v7soft.admin.service;

import cn.v7soft.admin.service.dto.OrderEmailDto;

/**
 * 邮件服务接口
 */
public interface IEmailService {

    /**
     * 发送订单确认邮件给客户
     *
     * @param dto 订单邮件信息
     */
    void sendOrderConfirmationEmail(OrderEmailDto dto);
}

