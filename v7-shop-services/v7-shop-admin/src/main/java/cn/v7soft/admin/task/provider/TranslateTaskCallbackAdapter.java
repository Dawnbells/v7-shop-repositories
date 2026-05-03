package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountRuntimeState;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateTaskStatus;
import cn.v7soft.admin.task.TranslateTaskContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider 和 AiAccountTranslateTask 之间的回调中间类。
 * <p>
 * 重试策略（统一）：
 * - onSubTaskFailed: retryable=true 且 attemptCount < 3 → 失败队列；否则 → 直接 FAILED
 */
@Slf4j
public class TranslateTaskCallbackAdapter implements TranslateProviderCallback {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final TranslateTaskContext taskContext;

    public TranslateTaskCallbackAdapter(TranslateTaskContext taskContext) {
        this.taskContext = taskContext;
    }

    @Override
    public void onSubTaskCompleted(AiAccountTranslateSubTask subTask, SubTaskResult result) {
        AiAccountRuntimeState runtimeState = taskContext.getOrCreateRuntimeState(subTask.getAiAccountId());
        try {
            log.debug("[TranslateTaskCallbackAdapter] subtask completion received: taskId={}, subTaskId={}, type={}, cacheHit={}, elapsedMs={}, businessCredits={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), result.isCacheHit(),
                    result.getElapsedMs(), result.getBusinessCredits());
            AiAccountTranslateTaskStatus status = taskContext.getTaskStatus(subTask.getTaskId());
            if (status == null) {
                log.debug("[TranslateTaskCallbackAdapter] completion ignored because parent task is missing: taskId={}, subTaskId={}",
                        subTask.getTaskId(), subTask.getSubTaskId());
                return;
            }

            taskContext.updateUsageRecord(subTask, result);
            if (!result.isCacheHit()) {
                taskContext.saveTranslationCache(subTask, result);
            }

            if (result.getTranslatedFile() != null) {
                status.completeImageSubTask(subTask, result.getTranslatedFile());
            } else if (result.getTranslatedHtml() != null) {
                status.completeHtmlSubTask(subTask, result.getTranslatedHtml());
            } else if (result.getTranslatedText() != null) {
                status.completeTextSubTask(subTask, result.getTranslatedText());
            } else {
                status.completeSubTask(subTask);
            }
            log.debug("[TranslateTaskCallbackAdapter] subtask completion applied: taskId={}, subTaskId={}, completed={}, failed={}, progress={}",
                    subTask.getTaskId(), subTask.getSubTaskId(),
                    status.getCompletedSubTaskCount().get(), status.getFailedSubTaskCount().get(), status.getProgress());
        } finally {
            runtimeState.releaseFinishedSlot();
            log.debug("[TranslateTaskCallbackAdapter] runtime slot released after completion: aiAccountId={}, subTaskId={}, inFlight={}",
                    subTask.getAiAccountId(), subTask.getSubTaskId(), runtimeState.getInFlightCount());
        }
    }

    @Override
    public void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable, SubTaskResult partialResult) {
        AiAccountRuntimeState runtimeState = taskContext.getOrCreateRuntimeState(subTask.getAiAccountId());
        try {
            log.debug("[TranslateTaskCallbackAdapter] subtask failure received: taskId={}, subTaskId={}, type={}, retryable={}, attempt={}, message={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), retryable,
                    subTask.getAttemptCount().get(), message);
            if (partialResult != null) {
                taskContext.accumulateUsageRecord(subTask, partialResult);
            }

            AiAccountTranslateTaskStatus status = taskContext.getTaskStatus(subTask.getTaskId());
            if (retryable && subTask.getAttemptCount().get() < MAX_RETRY_ATTEMPTS) {
                subTask.retry(message);
                if (status != null) {
                    status.retrySubTask(subTask, message);
                }
                taskContext.pushToFailedQueue(subTask);
                log.debug("[TranslateTaskCallbackAdapter] subtask scheduled for retry: taskId={}, subTaskId={}, nextAttempt={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), subTask.getAttemptCount().get() + 1);
            } else {
                subTask.fail(message);
                if (status != null) {
                    status.failSubTask(subTask, message);
                }
                log.debug("[TranslateTaskCallbackAdapter] subtask marked failed: taskId={}, subTaskId={}, retryable={}, attempt={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), retryable, subTask.getAttemptCount().get());
            }
        } finally {
            runtimeState.releaseFinishedSlot();
            log.debug("[TranslateTaskCallbackAdapter] runtime slot released after failure: aiAccountId={}, subTaskId={}, inFlight={}",
                    subTask.getAiAccountId(), subTask.getSubTaskId(), runtimeState.getInFlightCount());
        }
    }

    @Override
    public boolean isTaskActive(Long taskId) {
        return taskContext.getTaskStatus(taskId) != null;
    }

}
