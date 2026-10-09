package cn.v7soft.admin.service.impl;

import java.util.List;

import cn.v7soft.admin.controller.req.UpdateOrderLogisticsRequest;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.enums.OrderStatus;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.OrderRepository;
import cn.v7soft.dao.repositories.primary.TemporaryOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class OrderServiceLogisticsTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = spy(new OrderService(repository,
            mock(AsyncTaskRepository.class), mock(ITaskExecutorService.class),
            mock(TemporaryOrderRepository.class), mock(ICountryService.class)));

    @Test
    void blankValueKeepsOriginalAndFilledValueIsTrimmed() {
        Order first = order(1L);
        Order second = order(2L);
        givenOrders(first, second);

        service.updateOrderLogistics(request(List.of(1L, 2L, 1L), "  新渠道  ", "   ", false, false));

        for (Order order : List.of(first, second)) {
            assertThat(order.getLogisticsInfo().getDeliveryChannel()).isEqualTo("新渠道");
            assertThat(order.getLogisticsInfo().getStorehouse()).isEqualTo("原仓库");
            assertThat(order.getLogisticsInfo().getTrackingNumber()).isEqualTo("TN001");
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void clearFlagClearsFieldAndTakesPrecedenceOverValue() {
        Order first = order(1L);
        givenOrders(first);

        service.updateOrderLogistics(request(List.of(1L), "被忽略的渠道", "新仓库", true, false));

        assertThat(first.getLogisticsInfo().getDeliveryChannel()).isNull();
        assertThat(first.getLogisticsInfo().getStorehouse()).isEqualTo("新仓库");
        verify(repository).saveAll(List.of(first));
    }

    @Test
    void createsLogisticsInfoOnlyWhenWritingAValue() {
        Order writing = order(1L);
        writing.setLogisticsInfo(null);
        Order clearing = order(2L);
        clearing.setLogisticsInfo(null);
        givenOrders(writing);

        service.updateOrderLogistics(request(List.of(1L), null, "新仓库", true, false));

        assertThat(writing.getLogisticsInfo()).isNotNull();
        assertThat(writing.getLogisticsInfo().getDeliveryChannel()).isNull();
        assertThat(writing.getLogisticsInfo().getStorehouse()).isEqualTo("新仓库");

        givenOrders(clearing);

        service.updateOrderLogistics(request(List.of(2L), null, null, true, true));

        assertThat(clearing.getLogisticsInfo()).isNull();
    }

    @Test
    void refusesWhenNothingToChange() {
        assertThatThrownBy(() -> service.updateOrderLogistics(request(List.of(1L), " ", null, false, false)))
                .isInstanceOf(RuntimeException.class);

        verify(service, never()).findPaginated(any(QueryPageRequest.class));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void refusesWholeBatchWhenAnOrderIsMissingOrOutsideAccessScope() {
        Order first = order(1L);
        givenOrders(first);

        assertThatThrownBy(() -> service.updateOrderLogistics(request(List.of(1L, 2L), "新渠道", null, false, true)))
                .isInstanceOf(RuntimeException.class);

        assertThat(first.getLogisticsInfo().getDeliveryChannel()).isEqualTo("原渠道");
        assertThat(first.getLogisticsInfo().getStorehouse()).isEqualTo("原仓库");
        verify(repository, never()).saveAll(any());
    }

    private void givenOrders(Order... orders) {
        doReturn(new PageImpl<>(List.of(orders))).when(service)
                .findPaginated(any(QueryPageRequest.class));
    }

    private UpdateOrderLogisticsRequest request(List<Long> ids, String deliveryChannel, String storehouse,
                                                boolean clearDeliveryChannel, boolean clearStorehouse) {
        UpdateOrderLogisticsRequest request = new UpdateOrderLogisticsRequest();
        request.setIds(ids);
        request.setDeliveryChannel(deliveryChannel);
        request.setStorehouse(storehouse);
        request.setClearDeliveryChannel(clearDeliveryChannel);
        request.setClearStorehouse(clearStorehouse);
        return request;
    }

    private Order order(Long id) {
        return Order.builder().id(id)
                .itemInfos(List.of())
                .orderStatus(OrderStatus.PENDING)
                .logisticsInfo(OrderLogisticsInfo.builder()
                        .deliveryChannel("原渠道").storehouse("原仓库")
                        .trackingNumber("TN001").build())
                .build();
    }
}
