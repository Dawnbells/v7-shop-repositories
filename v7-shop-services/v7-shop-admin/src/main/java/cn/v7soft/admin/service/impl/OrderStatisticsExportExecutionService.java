package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
public class OrderStatisticsExportExecutionService {

    private final OrderStatisticsSnapshotService snapshotService;
    private final OrderStatisticsWorkbookService workbookService;
    private final IS3Service s3Service;
    private final IAsyncTaskService asyncTaskService;
    private final AsyncTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderStatisticsExportExecutionService(
            OrderStatisticsSnapshotService snapshotService,
            OrderStatisticsWorkbookService workbookService,
            IS3Service s3Service,
            IAsyncTaskService asyncTaskService,
            AsyncTaskRepository taskRepository,
            ObjectMapper objectMapper
    ) {
        this(
                snapshotService,
                workbookService,
                s3Service,
                asyncTaskService,
                taskRepository,
                objectMapper,
                Clock.systemUTC()
        );
    }

    OrderStatisticsExportExecutionService(
            OrderStatisticsSnapshotService snapshotService,
            OrderStatisticsWorkbookService workbookService,
            IS3Service s3Service,
            IAsyncTaskService asyncTaskService,
            AsyncTaskRepository taskRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.snapshotService = snapshotService;
        this.workbookService = workbookService;
        this.s3Service = s3Service;
        this.asyncTaskService = asyncTaskService;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void execute(AsyncTask task, SystemUserDto owner) {
        try {
            task.setMessage("正在读取统计快照");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 5);

            String resultToken = readResultToken(task.getParameters());
            OrderStatisticsStoredSnapshot snapshot = snapshotService.get(
                    owner.getCompanyId(),
                    owner.getLongId(),
                    resultToken
            );
            byte[] workbook = workbookService.create(snapshot.result());
            if (isCancelled(task.getId())) {
                return;
            }

            task.setMessage("正在上传 Excel");
            asyncTaskService.updateAsyncTask(task, TaskState.PROCESSING, 90);
            String relativePath = relativePath(owner.getCompanyId());
            s3Service.uploadExcel(workbook, relativePath);
            if (isCancelled(task.getId())) {
                return;
            }

            task.setExportRelativePath(relativePath);
            task.setMessage("导出完成，文件保留 24 小时");
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, 100);
        } catch (Throwable error) {
            if (isCancelled(task.getId())) {
                return;
            }
            log.error("订单统计导出失败: taskId={}", task.getId(), error);
            task.setMessage(error.getMessage());
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, 100);
        }
    }

    private boolean isCancelled(Long taskId) {
        return taskRepository.findById(taskId)
                .map(AsyncTask::getState)
                .filter(state -> state == TaskState.CANCELLED)
                .isPresent();
    }

    private String readResultToken(String parameters) {
        try {
            return objectMapper.readValue(
                    parameters,
                    OrderStatisticsExportSubmissionService.ExportParameters.class
            ).resultToken();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("统计导出参数不正确", exception);
        }
    }

    private String relativePath(Long companyId) {
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return "async-task/order-statistics/"
                + companyId + "/"
                + date.getYear() + "/"
                + twoDigits(date.getMonthValue()) + "/"
                + twoDigits(date.getDayOfMonth()) + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + ".xlsx";
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
