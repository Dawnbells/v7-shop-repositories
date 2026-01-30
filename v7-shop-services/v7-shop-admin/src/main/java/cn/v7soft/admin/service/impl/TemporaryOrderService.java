package cn.v7soft.admin.service.impl;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
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
    private TemporaryOrderService self;

    public TemporaryOrderService(TemporaryOrderRepository repository, ICompanyService companyService, SystemUserRepository systemUserRepository) {
        super(repository);
        this.companyService = companyService;
        this.systemUserRepository = systemUserRepository;
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
    public void synchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request) {
        try {
            lock.lock();
            self.doSynchronizeOrderFromExternalSystem(request);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void doSynchronizeOrderFromExternalSystem(EditTemporaryOrderRequest request) {
        log.debug("sync order: {}", JSONUtil.toJsonPrettyStr(request));
        SystemUser owner = systemUserRepository.findByUserName(request.getContextInfo().getSalesPerson())
                .orElse(SystemUser.builder().id(1L).name("系统").build());
        Company company = this.companyService.companyCached(request.getCompanyId());
        TenantContext.setCurrentTenant(company.getId(), company);
        Optional<TemporaryOrder> existed = findByOriginOrderId(request.getOriginOrderId());
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(existed.isEmpty(), "已存在相同的原始订单ID：" + request.getOriginOrderId());
        TemporaryOrder temporaryOrder = TemporaryOrder.builder().build();
        temporaryOrder.setFrom(request.getFrom());
        temporaryOrder.setFromUrl(request.getFromUrl());
        temporaryOrder.setPlatform(request.getPlatform());
        temporaryOrder.setOriginOrderId(request.getOriginOrderId());
        temporaryOrder.setOrderTime(request.getOrderTime());
        temporaryOrder.setPaymentInfo(request.toPaymentInfo());
        temporaryOrder.setFinancialInfo(request.toFinancialInfo());
        temporaryOrder.setDeliveryInfo(request.toDeliveryInfo());
        temporaryOrder.setContextInfo(request.toContextInfo(owner));
        temporaryOrder.setRiskInfo(request.toRiskInfo());
        temporaryOrder.setItemInfos(request.toItemInfos());
        temporaryOrder.setOwner(owner);
        saveAndFlush(temporaryOrder);
        temporaryOrder.getItemInfos().forEach(temporaryOrderItemInfo -> temporaryOrderItemInfo.setOrder(temporaryOrder));
        temporaryOrder.setOwner(owner);
        saveAndFlush(temporaryOrder);
    }
}
