package cn.v7soft.admin.controller;

import cn.v7soft.admin.service.impl.OrderStatisticsExportSubmissionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStatisticsExportControllerTest {

    @Test
    void submitsExportFromImmutableResultToken() {
        OrderStatisticsExportSubmissionService service =
                mock(OrderStatisticsExportSubmissionService.class);
        when(service.submit("token-1")).thenReturn(88L);
        OrderStatisticsExportController controller =
                new OrderStatisticsExportController(service);

        Long taskId = controller.export("token-1");

        assertThat(taskId).isEqualTo(88L);
        verify(service).submit("token-1");
    }
}
