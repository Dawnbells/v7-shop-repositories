package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderContextInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderDeliveryInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderFinancialInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderPaymentInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderRiskRecordInfoRequest;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 已脱敏订单再次同步（webhook orders/updated、手动同步）时不回填收货人与风控信息（ARCH §6.6）
 */
@ExtendWith(MockitoExtension.class)
class TemporaryOrderServiceRedactedSyncTest {

    private static final String ORIGIN_ORDER_ID = "5500000000001";

    @Mock private TemporaryOrderRepository repository;
    @Mock private ICompanyService companyService;
    @Mock private SystemUserRepository systemUserRepository;
    @Mock private OrderRepository orderRepository;

    private TemporaryOrderService service;

    @BeforeEach
    void setUp() {
        service = new TemporaryOrderService(repository, companyService, systemUserRepository, orderRepository);
        service.setSelf(service);
        Company company = Company.builder().build();
        company.setId(100L);
        when(companyService.companyCached(100L)).thenReturn(company);
        SystemUser owner = SystemUser.builder().id(88L).companyId(100L).name("张三").build();
        when(systemUserRepository.findById(88L)).thenReturn(Optional.of(owner));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TemporaryOrder existing(LocalDateTime piiRedactedAt) {
        OrderDeliveryInfo deliveryInfo = new OrderDeliveryInfo();
        deliveryInfo.setFirstName("[redacted]");
        deliveryInfo.setLastName("[redacted]");
        deliveryInfo.setPhone("");
        deliveryInfo.setCity("Los Angeles");
        TemporaryOrderRiskRecordInfo riskInfo = new TemporaryOrderRiskRecordInfo();
        riskInfo.setRemoteIp("");
        TemporaryOrder order = TemporaryOrder.builder()
                .originOrderId(ORIGIN_ORDER_ID)
                .from("Old-SHOPIFY")
                .deliveryInfo(deliveryInfo)
                .riskInfo(riskInfo)
                .piiRedactedAt(piiRedactedAt)
                .build();
        order.setId(1L);
        return order;
    }

    private EditTemporaryOrderRequest request() {
        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(100L);
        request.setFrom("TestShop-SHOPIFY");
        request.setFromUrl("");
        request.setPlatform(WebsiteTypeEnum.SHOPIFY);
        request.setOriginOrderId(ORIGIN_ORDER_ID);
        request.setOrderTime(LocalDateTime.of(2026, 6, 1, 22, 30));
        TemporaryOrderDeliveryInfoRequest deliveryInfo = new TemporaryOrderDeliveryInfoRequest();
        deliveryInfo.setFirstName("John");
        deliveryInfo.setLastName("Doe");
        deliveryInfo.setPhone("+1234567890");
        deliveryInfo.setCity("Los Angeles");
        request.setDeliveryInfo(deliveryInfo);
        TemporaryOrderFinancialInfoRequest financialInfo = new TemporaryOrderFinancialInfoRequest();
        financialInfo.setTotalAmount(new BigDecimal("99.99"));
        request.setFinancialInfo(financialInfo);
        request.setPaymentInfo(new TemporaryOrderPaymentInfoRequest());
        TemporaryOrderContextInfoRequest contextInfo = new TemporaryOrderContextInfoRequest();
        contextInfo.setSalesUid(88L);
        contextInfo.setSalesPerson("张三");
        request.setContextInfo(contextInfo);
        TemporaryOrderRiskRecordInfoRequest riskInfo = new TemporaryOrderRiskRecordInfoRequest();
        riskInfo.setRemoteIp("192.168.1.1");
        riskInfo.setUa("Mozilla/5.0");
        request.setRiskInfo(riskInfo);
        request.setItemInfos(List.of());
        return request;
    }

    @Test
    void shouldKeepRedactedDeliveryAndRiskInfo() {
        LocalDateTime redactedAt = LocalDateTime.of(2026, 9, 1, 0, 0);
        TemporaryOrder existing = existing(redactedAt);
        OrderDeliveryInfo redactedDelivery = existing.getDeliveryInfo();
        TemporaryOrderRiskRecordInfo redactedRisk = existing.getRiskInfo();
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(existing));

        assertFalse(service.synchronizeOrderFromExternalSystem(request(), true));

        assertSame(redactedDelivery, existing.getDeliveryInfo());
        assertEquals("[redacted]", existing.getDeliveryInfo().getFirstName());
        assertEquals("[redacted]", existing.getDeliveryInfo().getLastName());
        assertEquals("", existing.getDeliveryInfo().getPhone());
        assertSame(redactedRisk, existing.getRiskInfo());
        assertEquals("", existing.getRiskInfo().getRemoteIp());
        assertEquals(redactedAt, existing.getPiiRedactedAt());
        // 其余字段照常更新
        assertEquals("TestShop-SHOPIFY", existing.getFrom());
        assertEquals(0, new BigDecimal("99.99").compareTo(existing.getFinancialInfo().getTotalAmount()));
        verify(repository, atLeastOnce()).saveAndFlush(existing);
    }

    @Test
    void shouldOverwriteDeliveryAndRiskInfoWhenNotRedacted() {
        TemporaryOrder existing = existing(null);
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(existing));

        service.synchronizeOrderFromExternalSystem(request(), true);

        assertEquals("John", existing.getDeliveryInfo().getFirstName());
        assertEquals("+1234567890", existing.getDeliveryInfo().getPhone());
        assertEquals("192.168.1.1", existing.getRiskInfo().getRemoteIp());
    }
}
