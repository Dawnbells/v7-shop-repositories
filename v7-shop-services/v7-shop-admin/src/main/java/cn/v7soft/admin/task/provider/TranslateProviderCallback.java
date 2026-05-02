package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;

/**
 * Provider 向 AiAccountTranslateTask 通知子任务执行结果的回调接口。
 * <p>
 * 由 TranslateTaskCallbackAdapter 实现，所有回调方法内部会：
 * 1. 释放 AiAccountRuntimeState 并发槽
 * 2. 更新 AiAccountTranslateTaskStatus 状态
 * 3. 根据情况将子任务路由到失败队列或待执行队列
 */
public interface TranslateProviderCallback {

    /** Provider 完成子任务后调用；result 中包含翻译产物（文件/文本/HTML） */
    void onSubTaskCompleted(AiAccountTranslateSubTask subTask, SubTaskResult result);

    /**
     * Provider 子任务失败后调用。
     * retryable=true 且未超过最大重试次数时，adapter 会将子任务放入失败队列重试；
     * 否则标记为永久失败。
     */
    void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable);

    /**
     * Provider 检测到子任务过期后调用（如 TurboFlow lease 过期）。
     * 未超过最大重试次数：放入失败队列（优先重试）。
     * 已超过最大重试次数：重置 attemptCount，放入待执行队列队尾（重新排队）。
     */
    void onSubTaskExpired(AiAccountTranslateSubTask subTask, String reason);

    /** 检查父任务是否仍然存活（未被取消/删除），Provider 在分发前可调用此方法跳过已失效的任务 */
    boolean isTaskActive(Long taskId);
}
