package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountRuntimeState;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateTaskStatus;
import cn.v7soft.admin.task.TranslateTaskContext;
import lombok.extern.slf4j.Slf4j;

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
            AiAccountTranslateTaskStatus status = taskContext.getTaskStatus(subTask.getTaskId());
            if (status == null) {
                return;
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
        } finally {
            runtimeState.releaseFinishedSlot();
        }
    }

    @Override
    public void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable) {
        AiAccountRuntimeState runtimeState = taskContext.getOrCreateRuntimeState(subTask.getAiAccountId());
        try {
            AiAccountTranslateTaskStatus status = taskContext.getTaskStatus(subTask.getTaskId());
            if (retryable && subTask.getAttemptCount().get() < MAX_RETRY_ATTEMPTS) {
                subTask.retry(message);
                if (status != null) {
                    status.retrySubTask(subTask, message);
                }
                taskContext.pushToFailedQueue(subTask);
            } else {
                subTask.fail(message);
                if (status != null) {
                    status.failSubTask(subTask, message);
                }
            }
        } finally {
            runtimeState.releaseFinishedSlot();
        }
    }

    @Override
    public boolean isTaskActive(Long taskId) {
        return taskContext.getTaskStatus(taskId) != null;
    }

    @Override
    public void onSubTaskExpired(AiAccountTranslateSubTask subTask, String reason) {
        AiAccountRuntimeState runtimeState = taskContext.getOrCreateRuntimeState(subTask.getAiAccountId());
        try {
            AiAccountTranslateTaskStatus status = taskContext.getTaskStatus(subTask.getTaskId());
            if (subTask.getAttemptCount().get() < MAX_RETRY_ATTEMPTS) {
                subTask.retry(reason);
                if (status != null) {
                    status.retrySubTask(subTask, reason);
                }
                taskContext.pushToFailedQueue(subTask);
            } else {
                subTask.resetAttemptCount();
                subTask.retry(reason);
                if (status != null) {
                    status.retrySubTask(subTask, reason + " (re-queued)");
                }
                taskContext.pushToPendingQueue(subTask);
            }
        } finally {
            runtimeState.releaseFinishedSlot();
        }
    }
}
