package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.UpdateOrderStatusRequest;
import cn.v7soft.admin.controller.req.UpdateRemarkRequest;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.Order;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IOrderService extends IBaseDataRangeService<Order> {

    /**
     * 根据订单状态获取订单列表
     *
     * @param status 订单状态
     * @return 订单列表
     */
    List<Order> getOrdersByStatus(String status);

    /**
     * 根据用户ID分页获取订单列表
     *
     * @param userId 用户ID
     * @param pageable 分页信息
     * @return 分页订单
     */
    Page<Order> getOrdersByUserId(Long userId, Pageable pageable);

    /**
     * 更新订单状态
     *
     * @return
     */
    List<String> updateOrderStatus(UpdateOrderStatusRequest request);

    /**
     * 更新审单备注
     * @param request 审单备注请求
     */
    void updateOrderCheckRemark(UpdateRemarkRequest request);

    /**
     * 下载订单任务，返回任务ID
     * @param request 请求
     * @return 下单订单任务ID
     */
    Long download(DownloadOrderRequest request);

    Long upload(HttpServletRequest request);

    Optional<Order> findByOriginOrderId(String orderId);
}
