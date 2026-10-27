package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderContextInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderDeliveryInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderFinancialInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderPaymentInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderRiskRecordInfoRequest;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import cn.v7soft.dao.enums.BrowserPlatform;
import cn.v7soft.dao.enums.OrderStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 外部订单按原始订单 ID 更新：来源更新时间落库、UA 保留、已转正式订单不覆盖收货信息与审单字段
 */
@ExtendWith(MockitoExtension.class)
class TemporaryOrderServiceSyncUpdateTest {

    private static final String ORIGIN_ORDER_ID = "5500000000001";
    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)";

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

    private TemporaryOrder existing(boolean reviewed) {
        TemporaryOrderRiskRecordInfo riskInfo = new TemporaryOrderRiskRecordInfo();
        riskInfo.setRemoteIp("10.0.0.1");
        riskInfo.setUa(UA);
        riskInfo.setBrowserPlatform(BrowserPlatform.fromUaStr(UA));
        TemporaryOrder order = TemporaryOrder.builder()
                .originOrderId(ORIGIN_ORDER_ID)
                .riskInfo(riskInfo)
                .reviewed(reviewed)
                .build();
        order.setId(1L);
        return order;
    }

    /**
     * GraphQL 对账/手动同步形状的请求：没有 UA
     */
    private EditTemporaryOrderRequest request() {
        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(100L);
        request.setFrom("TestShop-SHOPIFY");
        request.setFromUrl("");
        request.setPlatform(WebsiteTypeEnum.SHOPIFY);
        request.setOriginOrderId(ORIGIN_ORDER_ID);
        request.setOrderTime(LocalDateTime.of(2026, 6, 1, 22, 30));
        request.setOriginUpdatedAt(LocalDateTime.of(2026, 6, 2, 9, 0));
        TemporaryOrderDeliveryInfoRequest deliveryInfo = new TemporaryOrderDeliveryInfoRequest();
        deliveryInfo.setFirstName("Shopify");
        deliveryInfo.setLastName("Address");
        deliveryInfo.setPhone("+1000000000");
        deliveryInfo.setCity("Los Angeles");
        request.setDeliveryInfo(deliveryInfo);
        TemporaryOrderFinancialInfoRequest financialInfo = new TemporaryOrderFinancialInfoRequest();
        financialInfo.setTotalAmount(new BigDecimal("120.00"));
        request.setFinancialInfo(financialInfo);
        request.setPaymentInfo(new TemporaryOrderPaymentInfoRequest());
        TemporaryOrderContextInfoRequest contextInfo = new TemporaryOrderContextInfoRequest();
        contextInfo.setSalesUid(88L);
        contextInfo.setSalesPerson("张三");
        request.setContextInfo(contextInfo);
        TemporaryOrderRiskRecordInfoRequest riskInfo = new TemporaryOrderRiskRecordInfoRequest();
        riskInfo.setRemoteIp("192.168.1.1");
        riskInfo.setUa("");
        riskInfo.setBrowserPlatform(BrowserPlatform.UNKNOWN);
        request.setRiskInfo(riskInfo);
        request.setItemInfos(List.of());
        return request;
    }

    @Test
    void shouldStoreOriginUpdatedAtAndKeepWebhookUserAgent() {
        TemporaryOrder existing = existing(false);
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(existing));

        assertFalse(service.synchronizeOrderFromExternalSystem(request(), true));

        assertEquals(LocalDateTime.of(2026, 6, 2, 9, 0), existing.getOriginUpdatedAt());
        // IP 照常更新，GraphQL 没有 UA 时保留 webhook 导入的 UA
        assertEquals("192.168.1.1", existing.getRiskInfo().getRemoteIp());
        assertEquals(UA, existing.getRiskInfo().getUa());
        assertEquals(BrowserPlatform.fromUaStr(UA), existing.getRiskInfo().getBrowserPlatform());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void shouldUpdateReviewedOrderWithoutTouchingDeliveryAndReviewFields() {
        TemporaryOrder existing = existing(true);
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(existing));
        OrderDeliveryInfo reviewedDelivery = new OrderDeliveryInfo();
        reviewedDelivery.setFirstName("审单改过");
        reviewedDelivery.setPhone("+8613800000000");
        OrderLogisticsInfo logisticsInfo = OrderLogisticsInfo.builder().waybillProductName("审单运单品名").build();
        Order order = Order.builder()
                .originOrderId(ORIGIN_ORDER_ID)
                .orderStatus(OrderStatus.CONFIRMED)
                .orderCheckRemark("已电话确认")
                .deliveryInfo(reviewedDelivery)
                .logisticsInfo(logisticsInfo)
                .financialInfo(new OrderFinancialInfo())
                .itemInfos(new ArrayList<>())
                .build();
        when(orderRepository.findByOriginOrderId(ORIGIN_ORDER_ID)).thenReturn(Optional.of(order));

        service.synchronizeOrderFromExternalSystem(request(), true);

        // 临时订单照常更新收货信息
        assertEquals("Shopify", existing.getDeliveryInfo().getFirstName());
        // 正式订单：收货信息、审单状态与备注、物流保持审单结果
        assertSame(reviewedDelivery, order.getDeliveryInfo());
        assertEquals("审单改过", order.getDeliveryInfo().getFirstName());
        assertEquals(OrderStatus.CONFIRMED, order.getOrderStatus());
        assertEquals("已电话确认", order.getOrderCheckRemark());
        assertEquals("审单运单品名", order.getLogisticsInfo().getWaybillProductName());
        // 其余信息照常更新
        assertEquals("TestShop-SHOPIFY", order.getFrom());
        assertEquals(0, new BigDecimal("120.00").compareTo(order.getFinancialInfo().getTotalAmount()));
        assertEquals(UA, order.getRiskInfo().getUa());
        verify(orderRepository).saveAndFlush(order);
    }
}
