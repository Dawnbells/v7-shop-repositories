package cn.v7soft.admin.service;

import org.springframework.scheduling.annotation.Async;

public interface ITaskExecutorService {

    /**
     * 提交异步任务，必须是PENDING状态。
     * 根据 TaskType 自动分发到批量翻译或即时翻译流程。
     */
    @Async("threadPoolTaskExecutor")
    void submitAsyncTask(Long taskId);

    @Async("threadPoolTaskExecutor")
    void resumeTranslateTask(Long taskId);

    /**
     * 服务启动时恢复所有未完成的任务（PENDING / PROCESSING）。
     */
    void recoverUnfinishedTasks();
}
