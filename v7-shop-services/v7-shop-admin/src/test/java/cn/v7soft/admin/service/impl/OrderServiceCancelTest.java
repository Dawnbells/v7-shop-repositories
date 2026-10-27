package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceCancelTest {
    @Mock private OrderRepository orderRepository;
    @Mock private TemporaryOrderRepository temporaryRepository;

    private OrderService orderService;
    private TemporaryOrderService temporaryService;
    private TemporaryOrder temporary;
    private Order staleOrder;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, mock(AsyncTaskRepository.class),
                mock(ITaskExecutorService.class), temporaryRepository, mock(ICountryService.class));
        temporaryService = new TemporaryOrderService(temporaryRepository, mock(ICompanyService.class),
                mock(SystemUserRepository.class), orderRepository);
        temporaryService.setSelf(temporaryService);
        temporary = TemporaryOrder.builder().id(1L).originOrderId("origin-1").customerCancelled(false).build();
        // Represents a bot-review snapshot read before cancellation.
        staleOrder = Order.builder().id(2L).originOrderId("origin-1")
                .orderStatus(OrderStatus.PENDING).itemInfos(List.of()).build();
    }

    @Test
    void shouldUseLatestCancellationWhenCancelCommitsBeforeConversion() {
        when(temporaryRepository.findByOriginOrderIdForUpdate("origin-1")).thenReturn(Optional.of(temporary));
        when(orderRepository.findByOriginOrderIdForUpdate("origin-1")).thenReturn(Optional.empty());
        assertTrue(temporaryService.markCustomerCancelled("origin-1"));
        assertEquals(OrderStatus.PENDING, staleOrder.getOrderStatus());

        when(temporaryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(temporary));
        when(orderRepository.saveAndFlush(staleOrder)).thenAnswer(invocation -> {
            assertTrue(staleOrder.getPlatformCancelled());
            return staleOrder;
        });
        Order saved = orderService.saveAndMarkReviewed(staleOrder, 1L);

        // 平台取消只写新字段，审单状态保持待审核
        assertTrue(saved.getPlatformCancelled());
        assertNotNull(saved.getPlatformCancelledAt());
        assertEquals(OrderStatus.PENDING, saved.getOrderStatus());
        var ordered = inOrder(temporaryRepository, orderRepository);
        ordered.verify(temporaryRepository).findByIdForUpdate(1L);
        ordered.verify(orderRepository, times(2)).saveAndFlush(staleOrder);
        ordered.verify(temporaryRepository).markAsReviewed(1L);
    }

    @Test
    void shouldCancelFormalOrderWhenConversionCommitsFirst() {
        when(temporaryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(temporary));
        when(orderRepository.saveAndFlush(staleOrder)).thenReturn(staleOrder);
        assertEquals(OrderStatus.PENDING, orderService.saveAndMarkReviewed(staleOrder, 1L).getOrderStatus());

        when(temporaryRepository.findByOriginOrderIdForUpdate("origin-1")).thenReturn(Optional.of(temporary));
        when(orderRepository.findByOriginOrderIdForUpdate("origin-1")).thenReturn(Optional.of(staleOrder));
        assertTrue(temporaryService.markCustomerCancelled("origin-1"));

        assertTrue(staleOrder.getPlatformCancelled());
        assertEquals(OrderStatus.PENDING, staleOrder.getOrderStatus());
        var ordered = inOrder(temporaryRepository, orderRepository);
        ordered.verify(temporaryRepository).findByOriginOrderIdForUpdate("origin-1");
        ordered.verify(orderRepository).findByOriginOrderIdForUpdate("origin-1");
        ordered.verify(orderRepository).saveAndFlush(staleOrder);
    }

    @Test
    void shouldKeepPendingForLegacyOrderWithNullCancellationFlag() {
        temporary.setCustomerCancelled(null);
        when(temporaryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(temporary));
        when(orderRepository.saveAndFlush(staleOrder)).thenReturn(staleOrder);

        Order saved = orderService.saveAndMarkReviewed(staleOrder, 1L);
        assertEquals(OrderStatus.PENDING, saved.getOrderStatus());
        assertFalse(saved.getPlatformCancelled());
    }

    @Test
    void shouldNotCreateOrderWhenTemporaryOrderIsMissing() {
        when(temporaryRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> orderService.saveAndMarkReviewed(staleOrder, 1L));
        verifyNoInteractions(orderRepository);
        verify(temporaryRepository, never()).markAsReviewed(anyLong());
    }
}
