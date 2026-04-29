package cn.v7soft.admin.service.impl;

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
        SystemUser owner = systemUserRepository.findByUserName(request.getContextInfo().getSalesPerson())
                .orElse(systemUserRepository.findByDeletedUserNames(request.getContextInfo().getSalesPerson())
                        .orElse(SystemUser.builder().id(1L).name("系统").build()));

        Company company = this.companyService.companyCached(request.getCompanyId());
        TenantContext.setCurrentTenant(company.getId(), company);
        Optional<TemporaryOrder> existed = findByOriginOrderId(request.getOriginOrderId());
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existed.isEmpty() || updateExisting,
                "已存在相同的原始订单ID：" + request.getOriginOrderId());

        boolean created = existed.isEmpty();
        TemporaryOrder temporaryOrder = existed.orElseGet(() -> TemporaryOrder.builder().build());
        fillTemporaryOrder(temporaryOrder, request, owner);
        saveAndFlush(temporaryOrder);

        if (!created && Boolean.TRUE.equals(temporaryOrder.getReviewed())) {
            updateReviewedOrder(temporaryOrder);
        }
        return created;
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
