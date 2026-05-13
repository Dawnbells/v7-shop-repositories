package cn.v7soft.admin.service;

import cn.v7soft.admin.service.dto.TemporaryOrderDto;

/**
 * 机器人审单策略
 */
public interface IBotOrderCheckService {

    void botReviewOrder(TemporaryOrderDto temporaryOrderDto);
}
