package cn.v7soft.admin.controller;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.EditOrderRequest;
import cn.v7soft.admin.controller.req.QueryOrderRequest;
import cn.v7soft.admin.controller.req.UpdateContactStatusRequest;
import cn.v7soft.admin.controller.req.UpdateOrderStatusRequest;
import cn.v7soft.admin.controller.req.UpdateRemarkRequest;
import cn.v7soft.admin.controller.resp.OrderResponse;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@Tag(name = "订单管理")
@RequestMapping("/orders")
public class OrderController extends BaseDataRangeController<Order, IOrderService, OrderResponse, QueryOrderRequest, EditOrderRequest> {

    protected OrderController(IOrderService service) {
        super(service);
    }

    @Override
    protected OrderResponse convertEntity(Order order) {
        return OrderResponse.convertEntity(order, true);
    }

    @Override
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<OrderResponse> page(@Valid @RequestBody QueryOrderRequest request) {
        String permission = getPermissionPrefix() + ".page";
        StpUtil.checkPermission(permission);
        return service.findPaginated(convertQueryPageRequest(request)).map(order -> filling(order, OrderResponse.convertEntity(order, !Objects.equals(Boolean.TRUE, request.getIsAudit()))));
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    protected QueryPageRequest<Order> convertQueryPageRequest(QueryOrderRequest request) {
        if (Objects.equals(Boolean.TRUE, request.getIsAudit()) && !StpUtil.hasPermission("order.audit")) {
            ClientResponseEnum.NO_PERMISSION.throwException();
        }
        return OrderQueryHelper.convertOrderQueryPageRequest(request, service);
    }

    @Override
    protected Order convertRequest(@Nullable Order dbEntity, EditOrderRequest request) {
        Order order = Optional.ofNullable(dbEntity).orElse(Order.builder().build());
        order.setFrom(request.getFrom());
        order.setFromUrl(request.getFromUrl());
        order.setPaymentInfo(request.getPaymentInfo());
        order.setFinancialInfo(request.getFinancialInfo());
        order.setDeliveryInfo(request.getDeliveryInfo());
        order.setContextInfo(request.getContextInfo());
        return order;
    }

    @SaCheckLogin
    @GetMapping("/{userId}/list")
    @Operation(summary = "根据用户ID查询订单")
    public Page<OrderResponse> getOrdersByUserId(@PathVariable Long userId, Pageable pageable) {
        return service.getOrdersByUserId(userId, pageable).map(this::convertEntity);
    }

    @SaCheckLogin
    @PostMapping("/updateOrderStatus")
    @Operation(summary = "更新订单状态")
    public List<String> updateOrderStatus(@RequestBody UpdateOrderStatusRequest request) {
        return service.updateOrderStatus(request);
    }

    @SaCheckLogin
    @PostMapping("/updateOrderCheckRemark")
    @Operation(summary = "更新订单备注")
    public void updateOrderCheckRemark(@RequestBody UpdateRemarkRequest request) {
        service.updateOrderCheckRemark(request);
    }

    @SaCheckLogin
    @PostMapping("/download")
    @Operation(summary = "下载订单")
    public Long download(@RequestBody @Valid DownloadOrderRequest request) {
        return service.download(request);
    }

    @SaCheckLogin
    @PostMapping("/upload")
    @Operation(summary = "上传订单")
    public Long upload(HttpServletRequest request) {
        return service.upload(request);
    }

    @SaCheckLogin
    @PostMapping("/updateContactStatus")
    @Operation(summary = "更新建联状态")
    public void updateContactStatus(@RequestBody UpdateContactStatusRequest request) {
        service.updateContactStatus(request);
    }

    @Override
    protected String getPermissionPrefix() {
        return "order";
    }
}
