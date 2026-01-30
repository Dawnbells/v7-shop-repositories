package cn.v7soft.admin.service;

import java.util.Optional;

import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.service.dto.TemporaryOrderDto;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.TemporaryOrder;

public interface ITemporaryOrderService extends IBaseDataRangeService<TemporaryOrder> {

    /**
     * 获取未同步的订单ID列表
     */
    Optional<TemporaryOrderDto> getNextBotPendingOrder();

    Optional<TemporaryOrder> findByOriginOrderId(String originOrderId);

    void synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request);
}
