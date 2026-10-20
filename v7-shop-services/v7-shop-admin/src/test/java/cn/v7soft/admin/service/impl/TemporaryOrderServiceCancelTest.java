package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryOrderServiceCancelTest {

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
    }

    private TemporaryOrder temporaryOrder(boolean cancelled) {
        TemporaryOrder temporaryOrder = TemporaryOrder.builder()
                .originOrderId(ORIGIN_ORDER_ID)
                .customerCancelled(cancelled)
                .build();
        temporaryOrder.setId(1L);
        return temporaryOrder;
    }

    private Order order(OrderStatus status, String remark) {
        return Order.builder()
                .originOrderId(ORIGIN_ORDER_ID)
                .orderStatus(status)
                .orderCheckRemark(remark)
                .itemInfos(new ArrayList<>())
                .build();
    }

    @Test
    void shouldReturnFalseWhenOrderNotSynced() {
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.empty());

        assertFalse(service.markCustomerCancelled(ORIGIN_ORDER_ID));
        verifyNoInteractions(orderRepository);
    }

    @Test
    void shouldMarkTemporaryOrderWhenNotReviewedYet() {
        TemporaryOrder temporaryOrder = temporaryOrder(false);
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(temporaryOrder));
        when(orderRepository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.empty());

        assertTrue(service.markCustomerCancelled(ORIGIN_ORDER_ID));
        assertTrue(temporaryOrder.getCustomerCancelled());
        // BaseDataRangeService.saveAndFlush 会对新旧实体各写一次，这里只关心有落库
        verify(repository, atLeastOnce()).saveAndFlush(temporaryOrder);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCancelPendingAndConfirmedOrders() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.PENDING, OrderStatus.CONFIRMED}) {
            Order order = order(status, "");
            when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(temporaryOrder(false)));
            when(orderRepository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(order));

            service.markCustomerCancelled(ORIGIN_ORDER_ID);

            assertEquals(OrderStatus.CUSTOMER_CANCELLED, order.getOrderStatus());
            assertEquals("", order.getOrderCheckRemark());
            verify(orderRepository).saveAndFlush(order);
        }
    }

    @Test
    void shouldKeepShippedStatusAndAppendRemark() {
        Order order = order(OrderStatus.SHIPPED, "原备注");
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(temporaryOrder(false)));
        when(orderRepository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(order));

        service.markCustomerCancelled(ORIGIN_ORDER_ID);

        assertEquals(OrderStatus.SHIPPED, order.getOrderStatus());
        assertEquals("原备注\nShopify 已取消", order.getOrderCheckRemark());
    }

    @Test
    void shouldTruncateLongRemarkToFitColumn() {
        Order order = order(OrderStatus.DELIVERED, "备".repeat(256));
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(temporaryOrder(false)));
        when(orderRepository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(order));

        service.markCustomerCancelled(ORIGIN_ORDER_ID);

        assertEquals(256, order.getOrderCheckRemark().length());
        assertTrue(order.getOrderCheckRemark().endsWith("\nShopify 已取消"));
    }

    @Test
    void shouldNotRewriteWhenAlreadyHandled() {
        Order order = order(OrderStatus.SHIPPED, "原备注\nShopify 已取消");
        TemporaryOrder temporaryOrder = temporaryOrder(true);
        when(repository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(temporaryOrder));
        when(orderRepository.findByOriginOrderIdForUpdate(ORIGIN_ORDER_ID)).thenReturn(Optional.of(order));

        assertFalse(service.markCustomerCancelled(ORIGIN_ORDER_ID));
        verify(repository, never()).saveAndFlush(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }
}
