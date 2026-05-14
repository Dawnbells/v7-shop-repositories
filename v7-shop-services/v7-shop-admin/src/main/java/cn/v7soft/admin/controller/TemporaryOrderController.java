package cn.v7soft.admin.controller;

import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.QueryOrderRequest;
import cn.v7soft.admin.controller.resp.OrderResponse;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.tenant.WebsiteContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@Tag(name = "订单管理")
@RequestMapping("/temporary-order")
public class TemporaryOrderController extends BaseDataRangeController<TemporaryOrder, ITemporaryOrderService,
        OrderResponse,
        QueryOrderRequest, EditTemporaryOrderRequest> {
    private final ICompanyService companyService;

    protected TemporaryOrderController(ITemporaryOrderService service, ICompanyService companyService) {
        super(service);
        this.companyService = companyService;
    }

    @Override
    protected OrderResponse convertEntity(TemporaryOrder order) {
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
    protected QueryPageRequest<TemporaryOrder> convertQueryPageRequest(QueryOrderRequest request) {
        if (Objects.equals(Boolean.TRUE, request.getIsAudit()) && !StpUtil.hasPermission("order.audit")) {
            ClientResponseEnum.NO_PERMISSION.throwException();
        }
        return QueryPageRequest.<TemporaryOrder>fromRequest(request)
                .addConstraint(WebsiteContext.isWebsiteAdmin(), EqualsQueryAttribute.builder()
                        .name("contextInfo.websiteId")
                        .value(WebsiteContext.getCurrentWebsiteId())
                        .build());
    }

    @Override
    protected TemporaryOrder convertRequest(@Nullable TemporaryOrder dbEntity, EditTemporaryOrderRequest request) {
        Optional<TemporaryOrder> existed = service.findByOriginOrderId(request.getOriginOrderId());
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existed.isEmpty(), "已存在相同的原始订单ID：" + request.getOriginOrderId());
        TemporaryOrder order = Optional.ofNullable(dbEntity).orElse(TemporaryOrder.builder().build());
        order.setFrom(request.getFrom());
        order.setFromUrl(request.getFromUrl());
        order.setPlatform(request.getPlatform());
        order.setOriginOrderId(request.getOriginOrderId());
        order.setOrderTime(request.getOrderTime());
        order.setPaymentInfo(request.toPaymentInfo());
        order.setFinancialInfo(request.toFinancialInfo());
        order.setDeliveryInfo(request.toDeliveryInfo());
        order.setContextInfo(request.toContextInfo(SystemUser.builder().id(1L).name("系统").build()));
        order.setRiskInfo(request.toRiskInfo());
        order.setItemInfos(request.toItemInfos());
        return order;
    }

    @Override
    protected String getPermissionPrefix() {
        return "order";
    }

    @PostMapping("/sync")
    @Operation(summary = "同步外部系统订单")
    public void synchronizeOrderFromExternalSystem(@Valid @RequestBody EditTemporaryOrderRequest request) {
        service.synchronizeOrderFromExternalSystem(request);
    }

}
