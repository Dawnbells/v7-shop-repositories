package cn.v7soft.admin.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.DownloadOrderRequest;
import cn.v7soft.admin.controller.req.EditOrderRequest;
import cn.v7soft.admin.controller.req.QueryOrderRequest;
import cn.v7soft.admin.controller.req.UpdateContactStatusRequest;
import cn.v7soft.admin.controller.req.UpdateOrderStatusRequest;
import cn.v7soft.admin.controller.req.UpdateOrderDepartmentRequest;
import cn.v7soft.admin.controller.req.UpdateOrderLogisticsRequest;
import cn.v7soft.admin.controller.req.UpdateRemarkRequest;
import cn.v7soft.admin.controller.resp.OrderResponse;
import cn.v7soft.admin.service.IOrderService;
import cn.v7soft.admin.service.IProtectedDataAccessLogService;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Department;
import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.DepartmentRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
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

    private final DepartmentRepository departmentRepository;
    private final IProtectedDataAccessLogService protectedDataAccessLogService;

    protected OrderController(IOrderService service, DepartmentRepository departmentRepository,
                              IProtectedDataAccessLogService protectedDataAccessLogService) {
        super(service);
        this.departmentRepository = departmentRepository;
        this.protectedDataAccessLogService = protectedDataAccessLogService;
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
        boolean desensitized = !Objects.equals(Boolean.TRUE, request.getIsAudit()) && !Objects.equals(Boolean.TRUE, request.getIsContact());
        Page<OrderResponse> responsePage = service.findPaginated(convertQueryPageRequest(request))
                .map(order -> filling(order, OrderResponse.convertEntity(order, desensitized)));
        fillPrivateDomainFlag(responsePage);
        recordShopifyListAccess(responsePage);
        return responsePage;
    }

    /**
     * 返回结果含 Shopify 订单时记录受保护客户数据访问日志，任何异常不影响响应
     */
    private void recordShopifyListAccess(Page<OrderResponse> responsePage) {
        try {
            List<Long> shopifyIds = responsePage.getContent().stream()
                    .filter(r -> r.getPlatform() == WebsiteTypeEnum.SHOPIFY && r.getId() != null)
                    .map(r -> Long.valueOf(r.getId()))
                    .toList();
            protectedDataAccessLogService.record(IProtectedDataAccessLogService.ACTION_LIST,
                    IProtectedDataAccessLogService.ORDER_TYPE_ORDER, shopifyIds);
        } catch (Exception e) {
            log.warn("订单列表访问日志记录失败", e);
        }
    }

    /**
     * 导出为异步任务：提交后按导出执行器相同的查询条件（当前用户 + 当前视图模式的数据范围）加 platform = SHOPIFY，
     * 取前 {@link IProtectedDataAccessLogService#MAX_STORED_IDS} 个订单 ID 与总数记录导出日志。任何异常不影响响应。
     */
    private void recordShopifyExportAccess(DownloadOrderRequest request) {
        try {
            if (request.getPlatform() != null && request.getPlatform() != WebsiteTypeEnum.SHOPIFY) {
                return;
            }
            // 复制一份请求，避免改动已提交的导出参数
            DownloadOrderRequest query = JSONUtil.toBean(JSONUtil.toJsonStr(request), DownloadOrderRequest.class);
            query.setPlatform(WebsiteTypeEnum.SHOPIFY);
            query.setPageNo(1);
            query.setPageSize(IProtectedDataAccessLogService.MAX_STORED_IDS);
            Page<Order> shopifyOrders = service.findPaginated(OrderQueryHelper.convertOrderQueryPageRequest(query, service),
                    SaSessionUtil.getLoginUser(), SaSessionUtil.getViewMode());
            List<Long> shopifyIds = shopifyOrders.getContent().stream().map(BaseEntity::getId).toList();
            protectedDataAccessLogService.record(IProtectedDataAccessLogService.ACTION_EXPORT,
                    IProtectedDataAccessLogService.ORDER_TYPE_ORDER, shopifyIds, shopifyOrders.getTotalElements());
        } catch (Exception e) {
            log.warn("订单导出访问日志记录失败", e);
        }
    }

    private void fillPrivateDomainFlag(Page<OrderResponse> responsePage) {
        Set<Long> departmentIds = responsePage.getContent().stream()
                .map(r -> r.getContextInfo() != null ? r.getContextInfo().getDepartmentId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (departmentIds.isEmpty()) {
            return;
        }
        Map<Long, Boolean> privateDomainMap = departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, d -> Boolean.TRUE.equals(d.getIsPrivateDomain())));
        for (OrderResponse resp : responsePage.getContent()) {
            Long deptId = resp.getContextInfo() != null ? resp.getContextInfo().getDepartmentId() : null;
            resp.setIsPrivateDomain(deptId != null ? privateDomainMap.getOrDefault(deptId, false) : false);
        }
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    protected QueryPageRequest<Order> convertQueryPageRequest(QueryOrderRequest request) {
        if (Objects.equals(Boolean.TRUE, request.getIsAudit()) && !StpUtil.hasPermission("order.audit")) {
            ClientResponseEnum.NO_PERMISSION.throwException();
        }
        if (Objects.equals(Boolean.TRUE, request.getIsContact())) {
            List<Long> privateDeptIds = departmentRepository.findAllPrivateDomainDepartmentIds();
            if (privateDeptIds.isEmpty()) {
                request.setBelongDepartmentIds(List.of("-1"));
            } else {
                List<String> deptIdStrings = privateDeptIds.stream().map(String::valueOf).toList();
                if (request.getBelongDepartmentIds() == null || request.getBelongDepartmentIds().isEmpty()) {
                    request.setBelongDepartmentIds(deptIdStrings);
                } else {
                    Set<String> privateSet = new HashSet<>(deptIdStrings);
                    List<String> intersection = request.getBelongDepartmentIds().stream()
                            .filter(privateSet::contains).toList();
                    request.setBelongDepartmentIds(intersection.isEmpty() ? List.of("-1") : intersection);
                }
            }
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
        Long taskId = service.download(request);
        recordShopifyExportAccess(request);
        return taskId;
    }

    @SaCheckLogin
    @PostMapping("/updateOrderDepartment")
    @Operation(summary = "批量修改订单部门显示名称")
    public void updateOrderDepartment(@Valid @RequestBody UpdateOrderDepartmentRequest request) {
        StpUtil.checkPermission("order.audit");
        service.updateOrderDepartment(request);
    }

    @SaCheckLogin
    @PostMapping("/updateOrderLogistics")
    @Operation(summary = "批量修改订单渠道/仓库")
    public void updateOrderLogistics(@Valid @RequestBody UpdateOrderLogisticsRequest request) {
        StpUtil.checkPermission("order.audit");
        service.updateOrderLogistics(request);
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

    @SaCheckLogin
    @PostMapping("/updateContactRemark")
    @Operation(summary = "更新建联备注")
    public void updateContactRemark(@RequestBody UpdateRemarkRequest request) {
        service.updateContactRemark(request);
    }

    @Override
    protected String getPermissionPrefix() {
        return "order";
    }
}
