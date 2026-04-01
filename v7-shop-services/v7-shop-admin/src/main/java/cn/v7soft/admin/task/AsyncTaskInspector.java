package cn.v7soft.admin.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskService;
import cn.v7soft.admin.service.impl.TranslateTaskMetrics;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 运行期异步任务巡检：定时扫描 PENDING / PROCESSING 状态的翻译任务，
 * 识别卡死任务并自动恢复或标记为失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskInspector {

    private static final long SLA_PROCESSING_MINUTES = 45;
    private static final long SLA_PENDING_MINUTES = 10;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    private final AsyncTaskRepository asyncTaskRepository;
    private final IAsyncTaskService asyncTaskService;
    private final ITaskService taskService;
    private final TranslateTaskMetrics translateTaskMetrics;

    /**
     * 每 5 分钟执行一次巡检
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    public void inspectStuckTasks() {
        List<AsyncTask> stuckTasks = asyncTaskRepository.findByStateIn(
                List.of(TaskState.PENDING, TaskState.PROCESSING));

        if (stuckTasks.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int recovered = 0;
        int failed = 0;

        for (AsyncTask task : stuckTasks) {
            try {
                LocalDateTime updateTime = task.getUpdateTime() != null ? task.getUpdateTime() : task.getCreateTime();
                if (updateTime == null) continue;

                long minutesSinceUpdate = Duration.between(updateTime, now).toMinutes();

                if (task.getState() == TaskState.PROCESSING && minutesSinceUpdate > SLA_PROCESSING_MINUTES) {
                    if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                            && task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                        log.warn("[TaskInspector] 任务 {} 处于 PROCESSING 超过 {} 分钟且有 batchJobName，尝试恢复",
                                task.getId(), minutesSinceUpdate);
                        taskService.resumeTranslateTask(task.getId());
                        recovered++;
                    } else {
                        log.warn("[TaskInspector] 任务 {} 处于 PROCESSING 超过 {} 分钟且无 batchJobName，标记失败",
                                task.getId(), minutesSinceUpdate);
                        task.setMessage("任务超时自动标记失败（巡检）");
                        asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                        translateTaskMetrics.recordTimeout();
                        failed++;
                    }
                } else if (task.getState() == TaskState.PENDING && minutesSinceUpdate > SLA_PENDING_MINUTES) {
                    log.warn("[TaskInspector] 任务 {} 处于 PENDING 超过 {} 分钟，重新投递",
                            task.getId(), minutesSinceUpdate);
                    asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                    taskService.submitAsyncTask(task.getId());
                    recovered++;
                }
            } catch (Exception e) {
                log.error("[TaskInspector] 巡检任务 {} 时发生异常", task.getId(), e);
            }
        }

        if (recovered > 0 || failed > 0) {
            log.info("[TaskInspector] 巡检完成: 恢复={}, 标记失败={}", recovered, failed);
        }
    }
}
