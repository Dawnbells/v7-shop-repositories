package cn.v7soft.admin.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TemporaryOrderService extends BaseDataRangeService<TemporaryOrder, TemporaryOrderRepository> implements ITemporaryOrderService {

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
     * 正式订单只标记平台已取消，不改审单状态与审单备注
     */
    private void applyCustomerCancelledToOrder(String originOrderId) {
        orderRepository.findByOriginOrderIdForUpdate(originOrderId).ifPresent(order -> {
            if (Boolean.TRUE.equals(order.getPlatformCancelled())) {
                return;
            }
            order.setPlatformCancelled(true);
            order.setPlatformCancelledAt(LocalDateTime.now());
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
        // 已脱敏（Shopify 合规删除/保留期到期）的订单再次同步时不回填收货人与风控 IP
        boolean piiRedacted = temporaryOrder.getPiiRedactedAt() != null;
        if (!piiRedacted) {
            temporaryOrder.setDeliveryInfo(request.toDeliveryInfo());
        }
        temporaryOrder.setContextInfo(request.toContextInfo(owner));
        if (!piiRedacted) {
            TemporaryOrderRiskRecordInfo previousRisk = temporaryOrder.getRiskInfo();
            TemporaryOrderRiskRecordInfo riskInfo = request.toRiskInfo();
            // Shopify GraphQL 不提供 UA，对账/手动同步更新时保留 webhook 导入时写入的 UA
            if (previousRisk != null && StrUtil.isBlank(riskInfo.getUa()) && StrUtil.isNotBlank(previousRisk.getUa())) {
                riskInfo.setUa(previousRisk.getUa());
                riskInfo.setBrowserPlatform(previousRisk.getBrowserPlatform());
            }
            temporaryOrder.setRiskInfo(riskInfo);
        }
        temporaryOrder.setOwner(owner);
        temporaryOrder.setCustomerCancelled(Boolean.TRUE.equals(request.getCustomerCancelled()));
        if (request.getOriginUpdatedAt() != null) {
            temporaryOrder.setOriginUpdatedAt(request.getOriginUpdatedAt());
        }

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

    /**
     * 已转正式订单：更新来源、金额、支付、商品、归属与风控信息；收货信息、审单状态与备注、机审结果、物流、建联记录以审单为准不覆盖
     */
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
            order.setContextInfo(latestOrder.getContextInfo());
            if (order.getPiiRedactedAt() == null) {
                order.setRiskInfo(latestOrder.getRiskInfo());
            }
            replaceOrderItems(order, latestOrder.getItemInfos());
            refreshOrderItemSummary(order);
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
