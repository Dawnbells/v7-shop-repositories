package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.task.AiAccountRuntimeState;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskState;
import cn.v7soft.admin.task.AiAccountTranslateTaskStatus;
import cn.v7soft.admin.task.TranslateTaskContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslateTaskCallbackAdapterTest {

    @Mock private TranslateTaskContext taskContext;
    @Mock private AiAccountTranslateTaskStatus status;

    @Test
    void codedTurboFlowFailureKeepsRetryingAfterThreeAttempts() {
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("1");
        request.setCountryId("1");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.image(1L, "99", request);

        for (int attempt = 1; attempt <= 3; attempt++) {
            subTask.dispatch("bridge", "assignment-" + attempt, LocalDateTime.now().plusMinutes(1));
            if (attempt < 3) {
                subTask.retry("dispatch failed");
            }
        }

        when(taskContext.getOrCreateRuntimeState(7L)).thenReturn(new AiAccountRuntimeState(7L));
        when(taskContext.getTaskStatus(1L)).thenReturn(status);

        new TranslateTaskCallbackAdapter(taskContext).onSubTaskFailed(
                subTask, "dispatch failed", true, null, "DISPATCH_FAILED");

        assertEquals(AiAccountTranslateSubTaskState.PENDING, subTask.getState());
        assertEquals(0, subTask.getAttemptCount().get());
        verify(status).retrySubTask(subTask, "dispatch failed");
        verify(taskContext).pushToFailedQueue(subTask);
        verify(status, never()).failSubTask(subTask, "dispatch failed");
    }

    @Test
    void queueInvariantErrorCodeStillStopsRetryingAfterThreeAttempts() {
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("1");
        request.setCountryId("1");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.image(3L, "100", request);

        for (int attempt = 1; attempt <= 3; attempt++) {
            subTask.dispatch("bridge", "assignment-" + attempt, LocalDateTime.now().plusMinutes(1));
            if (attempt < 3) {
                subTask.retry("non-image task in TurboFlow queue");
            }
        }

        when(taskContext.getOrCreateRuntimeState(7L)).thenReturn(new AiAccountRuntimeState(7L));
        when(taskContext.getTaskStatus(3L)).thenReturn(status);

        // 带错误码但在 LIMITED_RETRY_ERROR_CODES 里 —— 重试也还是同一个不变量被破，不该无限循环
        new TranslateTaskCallbackAdapter(taskContext).onSubTaskFailed(
                subTask, "non-image task in TurboFlow queue", true, null, "TURBOFLOW_QUEUE_INVARIANT");

        assertEquals(AiAccountTranslateSubTaskState.FAILED, subTask.getState());
        verify(status).failSubTask(subTask, "non-image task in TurboFlow queue");
        verify(taskContext, never()).pushToFailedQueue(subTask);
    }

    @Test
    void uncodedProviderFailureStillStopsRetryingAfterThreeAttempts() {
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("1");
        request.setCountryId("1");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.image(2L, "100", request);

        for (int attempt = 1; attempt <= 3; attempt++) {
            subTask.dispatch("bridge", "assignment-" + attempt, LocalDateTime.now().plusMinutes(1));
            if (attempt < 3) {
                subTask.retry("provider failed");
            }
        }

        when(taskContext.getOrCreateRuntimeState(7L)).thenReturn(new AiAccountRuntimeState(7L));
        when(taskContext.getTaskStatus(2L)).thenReturn(status);

        new TranslateTaskCallbackAdapter(taskContext).onSubTaskFailed(
                subTask, "provider failed", true, null, null);

        assertEquals(AiAccountTranslateSubTaskState.FAILED, subTask.getState());
        assertEquals(3, subTask.getAttemptCount().get());
        verify(status).failSubTask(subTask, "provider failed");
        verify(taskContext, never()).pushToFailedQueue(subTask);
    }
}
