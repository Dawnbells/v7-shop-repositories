package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.dao.enums.TaskState;

import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;

import java.io.InputStream;

public interface ITaskService {
    InputStream download(Long id);

    /**
     * 提交异步任务，必须是PENDING状态。
     * 根据 TaskType 自动分发到批量翻译或即时翻译流程。
     */
    @Async("threadPoolTaskExecutor")
    void submitAsyncTask(Long taskId);

    AsyncTaskResponse status(Long taskId);

    AsyncTaskResponse cancel(Long taskId);

    /**
     * 服务启动时恢复所有未完成的任务（PENDING / PROCESSING）。
     */
    void recoverUnfinishedTasks();

    Page<AsyncTaskResponse> list(TaskState state, int page, int size);

    Page<AsyncTaskResponse> unacknowledged(int page, int size);

    void acknowledge(Long taskId);

    void acknowledgeAllCompleted();

    /**
     * 将正在进行的批量翻译任务切换为即时翻译。
     * 仅当 Batch Job 所有请求仍为 pending 时才允许切换。
     * 修改 TaskType 为 PRODUCT_AI_TRANSLATE_DIRECT，重置 createTime，重新提交。
     */
    AsyncTaskResponse switchToDirectTranslate(Long taskId);

    @Async("threadPoolTaskExecutor")
    void resumeTranslateTask(Long taskId);

    /**
     * 重试失败/已取消的任务，重置 createTime（前端重新计时）。
     */
    AsyncTaskResponse retry(Long taskId);
}
