package cn.v7soft.admin.service.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.dto.TemporaryOrderDto;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.TemporaryOrderItemInfo;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TemporaryOrderService extends BaseDataRangeService<TemporaryOrder, TemporaryOrderRepository> implements ITemporaryOrderService {

    /**
     * 客户取消时允许自动改为“客户取消”的正式订单状态，其余状态只追加审单备注
     */
    private static final Set<OrderStatus> CANCELLABLE_ORDER_STATUSES = EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    private static final String CUSTOMER_CANCELLED_REMARK = "Shopify 已取消";
    private static final int ORDER_REMARK_MAX_LENGTH = 256;

    private final Lock lock = new ReentrantLock();
    private final ICompanyService companyService;
    private final SystemUserRepository systemUserRepository;
    private final OrderRepository orderRepository;
    private TemporaryOrderService self;

    public TemporaryOrderService(TemporaryOrderRepository repository, ICompanyService companyService, SystemUserRepository systemUserRepository,
                                 OrderRepository orderRepository) {
        super(repository);
        this.companyService = companyService;
        this.systemUserRepository = systemUserRepository;
        this.orderRepository = orderRepository;
    }

    @Lazy
    @Autowired
    public void setSelf(TemporaryOrderService self) {
        this.self = self;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TemporaryOrderDto> getNextBotPendingOrder() {
        Optional<TemporaryOrder> nextBotPendingOrder = repository.getNextBotPendingOrder();
        return nextBotPendingOrder.map(TemporaryOrderDto::convert);
    }

    @Override
    public Optional<TemporaryOrder> findByOriginOrderId(String originOrderId) {
        return repository.findByOriginOrderId(originOrderId);
    }

    @Override
    public boolean synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request) {
        return synchronizeOrderFromExternalSystem(request, false);
    }

    @Override
    public boolean synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request, boolean updateExisting) {
        try {
            lock.lock();
            return self.doSynchronizeOrderFromExternalSystem(request, updateExisting);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean doSynchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request, boolean updateExisting) {
//        log.debug("sync order: {}", JSONUtil.toJsonPrettyStr(request));
        Long salesUid = request.getContextInfo().getSalesUid();
        SystemUser owner = null;
        if (salesUid != null && salesUid > 0) {
            owner = systemUserRepository.findById(salesUid).orElse(null);
        }
        if (owner == null) {
            owner = systemUserRepository.findByUserName(request.getContextInfo().getSalesPerson())
                    .orElse(systemUserRepository.findByDeletedUserNames(request.getContextInfo().getSalesPerson())
                            .orElse(SystemUser.builder().id(1L).name("系统").build()));
        }

        Company company = this.companyService.companyCached(request.getCompanyId());
        TenantContext.setCurrentTenant(company.getId(), company);
        Optional<TemporaryOrder> existed = repository.findByOriginOrderIdForUpdate(request.getOriginOrderId());
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existed.isEmpty() || updateExisting,
                "已存在相同的原始订单ID：" + request.getOriginOrderId());

        boolean created = existed.isEmpty();
        TemporaryOrder temporaryOrder = existed.orElseGet(() -> TemporaryOrder.builder().build());
        fillTemporaryOrder(temporaryOrder, request, owner);
        saveAndFlush(temporaryOrder);

        if (!created && Boolean.TRUE.equals(temporaryOrder.getReviewed())) {
            updateReviewedOrder(temporaryOrder);
        }
        if (!created && Boolean.TRUE.equals(temporaryOrder.getCustomerCancelled())) {
            applyCustomerCancelledToOrder(temporaryOrder.getOriginOrderId());
        }
        return created;
    }

    @Override
    public boolean markCustomerCancelled(String originOrderId) {
        try {
            lock.lock();
            return self.doMarkCustomerCancelled(originOrderId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean doMarkCustomerCancelled(String originOrderId) {
        Optional<TemporaryOrder> existed = repository.findByOriginOrderIdForUpdate(originOrderId);
        if (existed.isEmpty()) {
            return false;
        }
        TemporaryOrder temporaryOrder = existed.get();
        boolean changed = !Boolean.TRUE.equals(temporaryOrder.getCustomerCancelled());
        if (changed) {
            temporaryOrder.setCustomerCancelled(true);
            saveAndFlush(temporaryOrder);
        }
        // 持有临时订单行锁直到事务提交，与机审转单使用相同的加锁顺序。
        applyCustomerCancelledToOrder(originOrderId);
        return changed;
    }

    /**
     * 待审核/已确认的正式订单改为客户取消；已发货等其他状态不改状态，只在审单备注中追加提示
     */
    private void applyCustomerCancelledToOrder(String originOrderId) {
        orderRepository.findByOriginOrderIdForUpdate(originOrderId).ifPresent(order -> {
            if (order.getOrderStatus() == OrderStatus.CUSTOMER_CANCELLED) {
                return;
            }
            if (CANCELLABLE_ORDER_STATUSES.contains(order.getOrderStatus())) {
                order.setOrderStatus(OrderStatus.CUSTOMER_CANCELLED);
            } else {
                String remark = StrUtil.blankToDefault(order.getOrderCheckRemark(), "");
                if (remark.contains(CUSTOMER_CANCELLED_REMARK)) {
                    return;
                }
                // order_remark 列长 256，超长时截断原备注以保留取消提示
                String kept = StrUtil.subPre(remark, ORDER_REMARK_MAX_LENGTH - CUSTOMER_CANCELLED_REMARK.length() - 1);
                order.setOrderCheckRemark(kept.isEmpty() ? CUSTOMER_CANCELLED_REMARK : kept + "\n" + CUSTOMER_CANCELLED_REMARK);
            }
            orderRepository.saveAndFlush(order);
        });
    }

    private void fillTemporaryOrder(TemporaryOrder temporaryOrder, EditTemporaryOrderRequest request, SystemUser owner) {
        temporaryOrder.setFrom(request.getFrom());
        temporaryOrder.setCompanyId(request.getCompanyId());
        temporaryOrder.setFromUrl(request.getFromUrl());
        temporaryOrder.setPlatform(request.getPlatform());
        temporaryOrder.setOriginOrderId(request.getOriginOrderId());
        temporaryOrder.setOrderTime(request.getOrderTime());
        temporaryOrder.setPaymentInfo(request.toPaymentInfo());
        temporaryOrder.setFinancialInfo(request.toFinancialInfo());
        temporaryOrder.setDeliveryInfo(request.toDeliveryInfo());
        temporaryOrder.setContextInfo(request.toContextInfo(owner));
        temporaryOrder.setRiskInfo(request.toRiskInfo());
        temporaryOrder.setOwner(owner);
        temporaryOrder.setCustomerCancelled(Boolean.TRUE.equals(request.getCustomerCancelled()));

        List<TemporaryOrderItemInfo> itemInfos = request.toItemInfos();
        if (temporaryOrder.getItemInfos() == null) {
            temporaryOrder.setItemInfos(new ArrayList<>());
        } else {
            temporaryOrder.getItemInfos().clear();
        }
        itemInfos.forEach(itemInfo -> {
            itemInfo.setOrder(temporaryOrder);
            temporaryOrder.getItemInfos().add(itemInfo);
        });
    }

    private void updateReviewedOrder(TemporaryOrder temporaryOrder) {
        orderRepository.findByOriginOrderId(temporaryOrder.getOriginOrderId()).ifPresent(order -> {
            Order latestOrder = TemporaryOrderDto.convert(temporaryOrder).toOrderInfo();
            order.setOwner(latestOrder.getOwner());
            order.setCompanyId(latestOrder.getCompanyId());
            order.setFrom(latestOrder.getFrom());
            order.setFromUrl(latestOrder.getFromUrl());
            order.setPlatform(latestOrder.getPlatform());
            order.setOriginOrderId(latestOrder.getOriginOrderId());
            order.setOrderTime(latestOrder.getOrderTime());
            order.setPaymentInfo(latestOrder.getPaymentInfo());
            order.setFinancialInfo(latestOrder.getFinancialInfo());
            order.setDeliveryInfo(latestOrder.getDeliveryInfo());
            order.setContextInfo(latestOrder.getContextInfo());
            order.setRiskInfo(latestOrder.getRiskInfo());
            replaceOrderItems(order, latestOrder.getItemInfos());
            refreshOrderItemSummary(order);
            if (order.getLogisticsInfo() != null && !latestOrder.getItemInfos().isEmpty()) {
                order.getLogisticsInfo().setWaybillProductName(latestOrder.getItemInfos().get(0).getWaybillProductName());
            }
            orderRepository.saveAndFlush(order);
        });
    }

    private void replaceOrderItems(Order order, List<OrderItemInfo> latestItemInfos) {
        if (order.getItemInfos() == null) {
            order.setItemInfos(new ArrayList<>());
        } else {
            order.getItemInfos().clear();
        }
        latestItemInfos.forEach(itemInfo -> {
            itemInfo.setOrder(order);
            order.getItemInfos().add(itemInfo);
        });
    }

    private void refreshOrderItemSummary(Order order) {
        order.setItemCount(order.getItemInfos().size());
        order.setSkuCodes(order.getItemInfos().stream()
                .collect(Collectors.groupingBy(OrderItemInfo::getSkuCode, Collectors.summingLong(OrderItemInfo::getQuantity)))
                .entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + "x" + e.getValue() : e.getKey())
                .collect(Collectors.joining("+")));
        order.setSkuNames(order.getItemInfos().stream()
                .collect(Collectors.groupingBy(OrderItemInfo::getSkuName, Collectors.summingLong(OrderItemInfo::getQuantity)))
                .entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + "x" + e.getValue() : e.getKey())
                .collect(Collectors.joining("+")));
        order.setQuantity(order.getItemInfos().stream().mapToLong(OrderItemInfo::getQuantity).sum());
    }
}
