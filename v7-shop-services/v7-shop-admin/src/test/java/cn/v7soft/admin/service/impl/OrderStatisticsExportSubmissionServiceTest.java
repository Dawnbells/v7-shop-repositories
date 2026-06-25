package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsExportSubmissionServiceTest {

    @Mock
    private AsyncTaskRepository taskRepository;
    @Mock
    private ITaskExecutorService taskExecutorService;
    @Mock
    private OrderStatisticsSnapshotService snapshotService;

    private MockedStatic<SaSessionUtil> saSessionUtil;
    private OrderStatisticsExportSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsExportSubmissionService(
                taskRepository,
                taskExecutorService,
                snapshotService,
                new ObjectMapper().findAndRegisterModules()
        );
        SystemUserDto user = SystemUserDto.builder()
                .id("101")
                .companyId(9L)
                .name("Alice")
                .build();
        saSessionUtil = mockStatic(SaSessionUtil.class);
        saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        saSessionUtil.close();
    }

    @Test
    void returnsExistingActiveExportForCurrentUser() {
        when(snapshotService.get(9L, 101L, "token-1")).thenReturn(null);
        AsyncTask existing = AsyncTask.builder()
                .id(88L)
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .state(TaskState.PROCESSING)
                .build();
        when(taskRepository.findByTaskTypeAndDedupKeyAndStateIn(
                TaskType.ORDER_STATISTICS_EXPORT,
                "ORDER_STATISTICS_EXPORT:101",
                List.of(TaskState.PENDING, TaskState.PROCESSING, TaskState.RESOLVED)
        )).thenReturn(List.of(existing));

        Long taskId = service.submit("token-1");

        assertThat(taskId).isEqualTo(88L);
        verify(taskRepository, never()).saveAndFlush(any());
        verify(taskExecutorService, never()).submitAsyncTask(any());
    }

    @Test
    void rejectsExpiredSnapshotBeforeCreatingTask() {
        when(snapshotService.get(9L, 101L, "expired"))
                .thenThrow(new IllegalArgumentException("统计结果已过期，请重新查询"));

        assertThatThrownBy(() -> service.submit("expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
        verify(taskRepository, never()).saveAndFlush(any());
    }
}
