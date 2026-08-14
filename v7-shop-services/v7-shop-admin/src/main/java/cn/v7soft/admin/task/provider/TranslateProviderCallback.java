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

    /** Provider 完成子任务后调用；result 中包含翻译产物和实际 token 用量 */
    void onSubTaskCompleted(AiAccountTranslateSubTask subTask, SubTaskResult result);

    /**
     * Provider 子任务失败后调用。
     * retryable=true 时，adapter 会将子任务放入失败队列重试；否则标记为永久失败。
     * partialResult 可为 null（表示失败时无 token 消耗）。
     * <p>
     * errorCode（可为 null）决定重试次数：
     * - 带错误码的 retryable 失败：adapter 不计入 attempt count → 永久重试。
     * - 未编码错误：沿用 attempt &lt; 3 限制。
     * - TranslateTaskCallbackAdapter.LIMITED_RETRY_ERROR_CODES 中的例外：带码也只给 3 次。
     * <p>
     * 内容政策阻断不走失败回调，而是以 policy-fallback completion 保留原文/原图
     * （SubTaskResult.policyFallbackReason 非空）。
     */
    void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable,
                         SubTaskResult partialResult, String errorCode);

    /** 检查父任务是否仍然存活（未被取消/删除），Provider 在分发前可调用此方法跳过已失效的任务 */
    boolean isTaskActive(Long taskId);
}
