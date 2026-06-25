package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderStatisticsExportSubmissionService {

    private static final List<TaskState> ACTIVE_STATES = List.of(
            TaskState.PENDING,
            TaskState.PROCESSING,
            TaskState.RESOLVED
    );

    private final AsyncTaskRepository taskRepository;
    private final ITaskExecutorService taskExecutorService;
    private final OrderStatisticsSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    public OrderStatisticsExportSubmissionService(
            AsyncTaskRepository taskRepository,
            ITaskExecutorService taskExecutorService,
            OrderStatisticsSnapshotService snapshotService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.taskExecutorService = taskExecutorService;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long submit(String resultToken) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        snapshotService.get(user.getCompanyId(), user.getLongId(), resultToken);
        String dedupKey = "ORDER_STATISTICS_EXPORT:" + user.getId();
        List<AsyncTask> active = taskRepository
                .findByTaskTypeAndDedupKeyAndStateIn(
                        TaskType.ORDER_STATISTICS_EXPORT,
                        dedupKey,
                        ACTIVE_STATES
                );
        if (!active.isEmpty()) {
            return active.get(0).getId();
        }

        AsyncTask task = AsyncTask.builder()
                .companyId(user.getCompanyId())
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(writeParameters(resultToken))
                .dedupKey(dedupKey)
                .name("订单统计导出")
                .viewMode(SaSessionUtil.getViewMode())
                .build()
                .fillOwner();
        task = taskRepository.saveAndFlush(task);
        taskExecutorService.submitAsyncTask(task.getId());
        return task.getId();
    }

    private String writeParameters(String resultToken) {
        try {
            return objectMapper.writeValueAsString(
                    new ExportParameters(resultToken)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("统计导出参数序列化失败", exception);
        }
    }

    public record ExportParameters(String resultToken) {
    }
}
