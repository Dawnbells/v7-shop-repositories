package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;

public interface TranslateProviderCallback {

    void onSubTaskCompleted(AiAccountTranslateSubTask subTask, SubTaskResult result);

    void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable);

    void onSubTaskExpired(AiAccountTranslateSubTask subTask, String reason);

    boolean isTaskActive(Long taskId);
}
