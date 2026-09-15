package cn.v7soft.admin.service.impl;

import java.util.List;

import cn.v7soft.admin.controller.req.UpdateOrderDepartmentRequest;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderContextInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
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

class OrderServiceDepartmentTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = spy(new OrderService(repository,
            mock(AsyncTaskRepository.class), mock(ITaskExecutorService.class),
            mock(TemporaryOrderRepository.class), mock(ICountryService.class)));

    @Test
    void changesDisplayNamesWhilePreservingOwnershipAndOrderStatus() {
        Order first = order(1L);
        Order second = order(2L);
        SystemUser owner = first.getOwner();
        doReturn(new PageImpl<>(List.of(first, second))).when(service)
                .findPaginated(any(QueryPageRequest.class));

        service.updateOrderDepartment(request(List.of(1L, 2L, 1L)));

        for (Order order : List.of(first, second)) {
            assertThat(order.getContextInfo().getDepartment()).isEqualTo("新部门");
            assertThat(order.getContextInfo().getDepartmentId()).isEqualTo(10L);
            assertThat(order.getContextInfo().getSalesUid()).isEqualTo(20L);
            assertThat(order.getContextInfo().getSalesPerson()).isEqualTo("销售员");
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }
        assertThat(first.getOwner()).isSameAs(owner);
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void refusesWholeBatchWhenAnOrderIsMissingOrOutsideAccessScope() {
        Order first = order(1L);
        doReturn(new PageImpl<>(List.of(first))).when(service)
                .findPaginated(any(QueryPageRequest.class));

        assertThatThrownBy(() -> service.updateOrderDepartment(request(List.of(1L, 2L))))
                .isInstanceOf(RuntimeException.class);

        assertThat(first.getContextInfo().getDepartment()).isEqualTo("原部门");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void validatesAllContextsBeforeChangingAnyName() {
        Order first = order(1L);
        Order second = order(2L);
        second.setContextInfo(null);
        doReturn(new PageImpl<>(List.of(first, second))).when(service)
                .findPaginated(any(QueryPageRequest.class));

        assertThatThrownBy(() -> service.updateOrderDepartment(request(List.of(1L, 2L))))
                .isInstanceOf(RuntimeException.class);

        assertThat(first.getContextInfo().getDepartment()).isEqualTo("原部门");
        verify(repository, never()).saveAll(any());
    }

    private UpdateOrderDepartmentRequest request(List<Long> ids) {
        UpdateOrderDepartmentRequest request = new UpdateOrderDepartmentRequest();
        request.setIds(ids);
        request.setDepartment("  新部门  ");
        return request;
    }

    private Order order(Long id) {
        return Order.builder().id(id)
                .itemInfos(List.of())
                .owner(SystemUser.builder().id(20L).build())
                .orderStatus(OrderStatus.PENDING)
                .contextInfo(OrderContextInfo.builder()
                        .department("原部门").departmentId(10L)
                        .salesUid(20L).salesPerson("销售员").build())
                .build();
    }
}
