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
     * retryable=true 且未超过最大重试次数(3)时，adapter 会将子任务放入失败队列重试；
     * 否则标记为永久失败。partialResult 可为 null（表示失败时无 token 消耗）。
     * <p>
     * errorCode（可为 null）用于标识失败类型：
     * - 环境型错误（如 RECAPTCHA_BLOCKED / FLOW_DISCONNECTED / GOOGLE_BLOCKED）：
     *   AI 端未真正报错，仅是连接/风控问题，adapter 不计入 attempt count → 永久重试。
     * - 其它（AI 真实拒绝、生成失败、超时等）：沿用 attempt &lt; 3 限制。
     */
    void onSubTaskFailed(AiAccountTranslateSubTask subTask, String message, boolean retryable,
                         SubTaskResult partialResult, String errorCode);

    /** 检查父任务是否仍然存活（未被取消/删除），Provider 在分发前可调用此方法跳过已失效的任务 */
    boolean isTaskActive(Long taskId);
}
