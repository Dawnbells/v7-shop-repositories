package cn.v7soft.admin.task;

/**
 * AiAccountTranslateTask 暴露给回调适配器的状态操作接口。
 * 由 AiAccountTranslateTask 实现，避免适配器直接持有 Task 引用。
 */
public interface TranslateTaskContext {

    AiAccountTranslateTaskStatus getTaskStatus(Long taskId);

    AiAccountRuntimeState getOrCreateRuntimeState(Long aiAccountId);

    void pushToFailedQueue(AiAccountTranslateSubTask subTask);

    void pushToPendingQueue(AiAccountTranslateSubTask subTask);
}
