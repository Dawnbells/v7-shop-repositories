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

    boolean synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request);

    boolean synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request, boolean updateExisting);

    /**
     * 将已同步的订单标记为客户取消，已生成的正式订单只标记平台已取消（不改审单状态）
     * @return 订单已同步且本次新标记为取消时返回 true
     */
    boolean markCustomerCancelled(String originOrderId);
}
