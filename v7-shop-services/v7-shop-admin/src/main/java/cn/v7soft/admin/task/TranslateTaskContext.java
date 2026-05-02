package cn.v7soft.admin.task;

import cn.v7soft.admin.task.provider.SubTaskResult;

/**
 * AiAccountTranslateTask 暴露给回调适配器的状态操作接口。
 * 由 AiAccountTranslateTask 实现，避免适配器直接持有 Task 引用。
 */
public interface TranslateTaskContext {

    AiAccountTranslateTaskStatus getTaskStatus(Long taskId);

    AiAccountRuntimeState getOrCreateRuntimeState(Long aiAccountId);

    void pushToFailedQueue(AiAccountTranslateSubTask subTask);

    void pushToPendingQueue(AiAccountTranslateSubTask subTask);

    /** 子任务完成时，更新对应的 AiTranslateUsageRecord（写入实际 token、credits） */
    void updateUsageRecord(AiAccountTranslateSubTask subTask, SubTaskResult result);

    /** 子任务失败/重试时，累加实际 token 用量到 AiTranslateUsageRecord */
    void accumulateUsageRecord(AiAccountTranslateSubTask subTask, SubTaskResult partialResult);

    /** 子任务完成后，将翻译结果保存到翻译缓存（TEXT/HTML → TextTranslationCache，IMAGE → ImageTranslationCache） */
    void saveTranslationCache(AiAccountTranslateSubTask subTask, SubTaskResult result);
}
