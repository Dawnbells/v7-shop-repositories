package cn.v7soft.admin.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.impl.GeminiQuotaTracker;
import cn.v7soft.admin.service.impl.TaskExecutorService;
import cn.v7soft.admin.service.impl.TranslateTaskMetrics;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskInspector {

    private static final long SLA_PROCESSING_MINUTES = 45;
    private static final long SLA_PENDING_MINUTES = 10;
    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    private final AsyncTaskRepository asyncTaskRepository;
    private final IAsyncTaskService asyncTaskService;
    private final ITaskExecutorService taskExecutorService;
    private final TranslateTaskMetrics translateTaskMetrics;
    private final GeminiQuotaTracker geminiQuotaTracker;
    private final SystemUserRepository systemUserRepository;

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
        int batchChecked = 0;

        for (AsyncTask task : stuckTasks) {
            try {
                if (task.getTaskType() == TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE) {
                    continue;
                }

                LocalDateTime updateTime = task.getUpdateTime() != null ? task.getUpdateTime() : task.getCreateTime();
                if (updateTime == null) continue;

                long minutesSinceUpdate = Duration.between(updateTime, now).toMinutes();

                if (task.getState() == TaskState.PROCESSING) {
                    if (isBatchTranslateTask(task)) {
                        log.info("[TaskInspector] 检查 Batch 任务: taskId={}, jobName={}", task.getId(), task.getBatchJobName());
                        taskExecutorService.resumeTranslateTask(task.getId());
                        batchChecked++;
                    } else if (minutesSinceUpdate > SLA_PROCESSING_MINUTES) {
                        log.warn("[TaskInspector] 任务 {} 处于 PROCESSING 超过 {} 分钟且无 batchJobName，标记失败",
                                task.getId(), minutesSinceUpdate);
                        task.setMessage("任务超时自动标记失败（巡检）");
                        asyncTaskService.updateAsyncTask(task, TaskState.FAILED, COMPLETED_OR_FAILED_PROGRESS);
                        translateTaskMetrics.recordTimeout();
                        failed++;
                    }
                } else if (task.getState() == TaskState.PENDING) {
                    if (isQuotaExhaustedPending(task)) {
                        if (!geminiQuotaTracker.isAllExhausted()) {
                            log.info("[TaskInspector] 配额已恢复, 重新投递任务: taskId={}", task.getId());
                            task.setMessage(null);
                            asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                            taskExecutorService.submitAsyncTask(task.getId());
                            recovered++;
                        } else {
                            log.debug("[TaskInspector] 配额仍耗尽, 跳过任务: taskId={}", task.getId());
                        }
                    } else if (isBatchQueuedPending(task)) {
                        log.info("[TaskInspector] 批量排队任务重新投递: taskId={}", task.getId());
                        task.setMessage(null);
                        asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                        taskExecutorService.submitAsyncTask(task.getId());
                        recovered++;
                    } else if (minutesSinceUpdate > SLA_PENDING_MINUTES) {
                        log.warn("[TaskInspector] 任务 {} 处于 PENDING 超过 {} 分钟，重新投递",
                                task.getId(), minutesSinceUpdate);
                        asyncTaskService.updateAsyncTask(task, TaskState.PENDING, 0);
                        taskExecutorService.submitAsyncTask(task.getId());
                        recovered++;
                    }
                }
            } catch (Exception e) {
                log.error("[TaskInspector] 巡检任务 {} 时发生异常", task.getId(), e);
            }
        }

        if (recovered > 0 || failed > 0 || batchChecked > 0) {
            log.info("[TaskInspector] 巡检完成: 恢复={}, 标记失败={}, Batch检查={}", recovered, failed, batchChecked);
        }
    }

    private boolean isBatchTranslateTask(AsyncTask task) {
        return task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                && task.getBatchJobName() != null && !task.getBatchJobName().isBlank();
    }

    private boolean isQuotaExhaustedPending(AsyncTask task) {
        return isTranslateTask(task)
                && TaskExecutorService.QUOTA_EXHAUSTED_MSG.equals(task.getMessage());
    }

    private boolean isBatchQueuedPending(AsyncTask task) {
        return task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                && task.getMessage() != null
                && task.getMessage().contains("批量任务队列已满");
    }

    private boolean isTranslateTask(AsyncTask task) {
        return task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                || task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE_DIRECT;
    }

    @Transactional
    @Scheduled(cron = "0 0 0 1 * ?")
    public void resetMonthlyCredits() {
        int rows = systemUserRepository.resetAllCredits();
        log.info("[creditsReset] 已重置 {} 个用户的AI额度", rows);
    }
}
