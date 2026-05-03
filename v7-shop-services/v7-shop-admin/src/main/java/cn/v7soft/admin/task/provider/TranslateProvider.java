package cn.v7soft.admin.task.provider;

import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;

/**
 * AI 翻译子任务的 Provider 接口。
 * <p>
 * 每种 AiProvider 枚举值对应一个实现（如 TurboFlowBridgeProvider）。
 * AiAccountTranslateTask 的子任务执行定时器通过 providerRegistry 查找对应 Provider，
 * 调用 executeSubTask 分发子任务。Provider 完成后通过 callback 通知结果。
 * <p>
 * 引用链路：AiAccountTranslateTask -> Provider -> TranslateTaskCallbackAdapter -> AiAccountTranslateTask
 */
public interface TranslateProvider {

    AiProvider getProviderType();

    void setCallback(TranslateProviderCallback callback);

    /**
     * 估算单个子任务所需的积分。在 loadTask 阶段按子任务累加，用于任务级冻结。
     */
    int estimateSubTaskCredits(AiAccount account, AiAccountTranslateSubTask subTask);

    /**
     * 分发子任务给 Provider 执行。
     * 调用前，AiAccountTranslateTask 已通过 AiAccountRuntimeState.reserveSlots 预留并发槽。
     * Provider 执行完成后，必须通过 callback 的 onSubTaskCompleted/onSubTaskFailed 通知结果，
     * 由 adapter 释放并发槽。
     * <p>
     * 对于异步 Provider（如 TurboFlow），此方法仅将子任务存入内部队列，实际执行由外部触发（如 plugin poll）。
     */
    void executeSubTask(AiAccountTranslateSubTask subTask);

    /**
     * 回收过期的子任务分配。由 syncTaskStatus 定时器周期性调用。
     * Provider 自行决定过期子任务的处理（通过 callback.onSubTaskFailed 重试或标记失败）。
     */
    default void reclaimExpiredAssignments() {
    }

    /**
     * 任务取消时调用（settleTask 之前）。
     * Provider 应清理属于该 taskId 的 in-flight 子任务：
     * - 未提交到 API 的子任务：callback.onSubTaskFailed(retryable=false, partialResult=null)
     * - 已提交/可能消耗 API 的子任务：callback.onSubTaskFailed(retryable=false, partialResult=estimated)
     */
    default void onTaskCancelling(Long taskId) {
    }
}
