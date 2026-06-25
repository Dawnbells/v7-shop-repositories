package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.resp.OrderStatisticsQueryJobResponse;
import cn.v7soft.admin.service.impl.OrderStatisticsQueryJob;
import cn.v7soft.admin.service.impl.OrderStatisticsQueryJobService;
import cn.v7soft.admin.service.impl.OrderStatisticsQueryJobState;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderStatisticsQueryJobControllerTest {

    private OrderStatisticsQueryJobService jobService;
    private OrderStatisticsQueryJobController controller;
    private MockedStatic<SaSessionUtil> saSessionUtil;

    @BeforeEach
    void setUp() {
        jobService = mock(OrderStatisticsQueryJobService.class);
        controller = new OrderStatisticsQueryJobController(jobService);
        SystemUserDto user = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .build();
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        saSessionUtil.close();
    }

    @Test
    void statusReturnsResultTokenForCurrentOwner() {
        when(jobService.status(9L, 101L, "job-1")).thenReturn(completed());

        OrderStatisticsQueryJobResponse response = controller.status("job-1");

        assertThat(response.getState()).isEqualTo(OrderStatisticsQueryJobState.COMPLETED);
        assertThat(response.getResultToken()).isEqualTo("token-1");
        verify(jobService).status(9L, 101L, "job-1");
    }

    @Test
    void cancelUsesCurrentOwnerScope() {
        OrderStatisticsQueryJob cancelled = new OrderStatisticsQueryJob(
                9L,
                101L,
                "job-1",
                OrderStatisticsQueryJobState.CANCELLED,
                Instant.parse("2026-06-25T12:00:00Z"),
                Instant.parse("2026-06-25T12:01:00Z"),
                null,
                "已取消"
        );
        when(jobService.cancel(9L, 101L, "job-1")).thenReturn(cancelled);

        OrderStatisticsQueryJobResponse response = controller.cancel("job-1");

        assertThat(response.getState()).isEqualTo(OrderStatisticsQueryJobState.CANCELLED);
        verify(jobService).cancel(9L, 101L, "job-1");
    }

    private OrderStatisticsQueryJob completed() {
        return new OrderStatisticsQueryJob(
                9L,
                101L,
                "job-1",
                OrderStatisticsQueryJobState.COMPLETED,
                Instant.parse("2026-06-25T12:00:00Z"),
                Instant.parse("2026-06-25T12:01:00Z"),
                "token-1",
                null
        );
    }
}
