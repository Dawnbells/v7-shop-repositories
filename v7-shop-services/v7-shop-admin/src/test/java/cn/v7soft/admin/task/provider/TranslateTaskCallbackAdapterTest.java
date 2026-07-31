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
    void dispatchFailureStopsRetryingAfterThreeAttempts() {
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

        assertEquals(AiAccountTranslateSubTaskState.FAILED, subTask.getState());
        assertEquals(3, subTask.getAttemptCount().get());
        verify(status).failSubTask(subTask, "dispatch failed");
        verify(taskContext, never()).pushToFailedQueue(subTask);
    }
}