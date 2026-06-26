package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.OrderStatisticsPageRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsPageResponse;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.service.IOrderStatisticsConfigService;
import cn.v7soft.admin.service.impl.OrderStatisticsOptionService;
import cn.v7soft.admin.service.impl.OrderStatisticsSubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderStatisticsQueryHttpStatusTest {

    @Test
    void groupsPageDelegatesToSubmissionService() {
        OrderStatisticsSubmissionService submissionService =
                mock(OrderStatisticsSubmissionService.class);
        OrderStatisticsPageRequest request = new OrderStatisticsPageRequest();
        OrderStatisticsPageResponse<OrderStatisticsGroupResponse> expected =
                OrderStatisticsPageResponse.<OrderStatisticsGroupResponse>builder()
                        .list(List.of(OrderStatisticsGroupResponse.builder()
                                .groupKey("EMPLOYEE:101")
                                .name("Alice")
                                .build()))
                        .total(1)
                        .pageNo(1)
                        .pageSize(20)
                        .totalPages(1)
                        .build();
        when(submissionService.groupsPage("token-1", request)).thenReturn(expected);
        OrderStatisticsController controller = new OrderStatisticsController(
                mock(IOrderStatisticsConfigService.class),
                mock(OrderStatisticsOptionService.class),
                submissionService
        );

        OrderStatisticsPageResponse<OrderStatisticsGroupResponse> response =
                controller.groupsPage("token-1", request);

        assertThat(response).isSameAs(expected);
    }

    @Test
    void bucketGroupsPageDelegatesToSubmissionService() {
        OrderStatisticsSubmissionService submissionService =
                mock(OrderStatisticsSubmissionService.class);
        OrderStatisticsPageRequest request = new OrderStatisticsPageRequest();
        OrderStatisticsPageResponse<OrderStatisticsBucketGroupResponse> expected =
                OrderStatisticsPageResponse.<OrderStatisticsBucketGroupResponse>builder()
                        .list(List.of(OrderStatisticsBucketGroupResponse.builder()
                                .bucketKey("2026-06-01")
                                .groupKey("EMPLOYEE:101")
                                .name("Alice")
                                .build()))
                        .total(1)
                        .pageNo(1)
                        .pageSize(20)
                        .totalPages(1)
                        .build();
        when(submissionService.bucketGroupsPage("token-1", request)).thenReturn(expected);
        OrderStatisticsController controller = new OrderStatisticsController(
                mock(IOrderStatisticsConfigService.class),
                mock(OrderStatisticsOptionService.class),
                submissionService
        );

        OrderStatisticsPageResponse<OrderStatisticsBucketGroupResponse> response =
                controller.bucketGroupsPage("token-1", request);

        assertThat(response).isSameAs(expected);
    }
    @Test
    void processingQueryReturnsAccepted() {
        OrderStatisticsSubmissionService submissionService =
                mock(OrderStatisticsSubmissionService.class);
        OrderStatisticsQueryRequest request = new OrderStatisticsQueryRequest();
        when(submissionService.submit(request)).thenReturn(
                OrderStatisticsQueryResponse.builder()
                        .state("PROCESSING")
                        .queryJobId("job-1")
                        .build()
        );
        OrderStatisticsController controller = new OrderStatisticsController(
                mock(IOrderStatisticsConfigService.class),
                mock(OrderStatisticsOptionService.class),
                submissionService
        );

        ResponseEntity<OrderStatisticsQueryResponse> response =
                controller.query(request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void completedQueryReturnsOk() {
        OrderStatisticsSubmissionService submissionService =
                mock(OrderStatisticsSubmissionService.class);
        OrderStatisticsQueryRequest request = new OrderStatisticsQueryRequest();
        when(submissionService.submit(request)).thenReturn(
                OrderStatisticsQueryResponse.builder()
                        .state("COMPLETED")
                        .resultToken("token-1")
                        .build()
        );
        OrderStatisticsController controller = new OrderStatisticsController(
                mock(IOrderStatisticsConfigService.class),
                mock(OrderStatisticsOptionService.class),
                submissionService
        );

        ResponseEntity<OrderStatisticsQueryResponse> response =
                controller.query(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
