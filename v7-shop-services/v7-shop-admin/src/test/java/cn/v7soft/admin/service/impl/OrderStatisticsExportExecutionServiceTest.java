package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsExportExecutionServiceTest {

    @Mock
    private OrderStatisticsSnapshotService snapshotService;
    @Mock
    private OrderStatisticsWorkbookService workbookService;
    @Mock
    private IS3Service s3Service;
    @Mock
    private IAsyncTaskService asyncTaskService;
    @Mock
    private AsyncTaskRepository taskRepository;

    private OrderStatisticsExportExecutionService service;
    private AsyncTask task;
    private SystemUserDto owner;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsExportExecutionService(
                snapshotService,
                workbookService,
                s3Service,
                asyncTaskService,
                taskRepository,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC)
        );
        task = AsyncTask.builder()
                .id(55L)
                .companyId(9L)
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .state(TaskState.PENDING)
                .parameters("{\"resultToken\":\"token-1\"}")
                .build();
        owner = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .build();
    }

    @Test
    void uploadsWorkbookAndCompletesTask() {
        OrderStatisticsResultResponse result =
                OrderStatisticsResultResponse.builder().build();
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(snapshot(result));
        when(workbookService.create(result)).thenReturn(new byte[]{1, 2, 3});
        when(taskRepository.findById(55L)).thenReturn(Optional.of(
                AsyncTask.builder().id(55L).state(TaskState.PROCESSING).build()
        ));

        service.execute(task, owner);

        verify(s3Service).uploadExcel(
                eq(new byte[]{1, 2, 3}),
                contains("async-task/order-statistics/9/2026/06/25/")
        );
        verify(asyncTaskService).updateAsyncTask(task, TaskState.COMPLETED, 100);
    }

    @Test
    void cancelledTaskDoesNotUpload() {
        OrderStatisticsResultResponse result =
                OrderStatisticsResultResponse.builder().build();
        when(snapshotService.get(9L, 101L, "token-1"))
                .thenReturn(snapshot(result));
        when(workbookService.create(result)).thenReturn(new byte[]{1, 2, 3});
        when(taskRepository.findById(55L)).thenReturn(Optional.of(
                AsyncTask.builder().id(55L).state(TaskState.CANCELLED).build()
        ));

        service.execute(task, owner);

        verify(s3Service, never()).uploadExcel(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private OrderStatisticsStoredSnapshot snapshot(
            OrderStatisticsResultResponse result
    ) {
        return new OrderStatisticsStoredSnapshot(
                9L,
                101L,
                "token-1",
                Instant.parse("2026-06-25T11:50:00Z"),
                Instant.parse("2026-06-25T12:20:00Z"),
                result
        );
    }
}
