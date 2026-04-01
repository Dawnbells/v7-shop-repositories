package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.dao.enums.TaskState;

import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;

import java.io.InputStream;

public interface ITaskService {
    InputStream download(Long id);

    /**
     * 提交异步任务，必须是PENDING状态
     * @param taskId 异步任务ID
     */
    @Async
    void submitAsyncTask(Long taskId);

    AsyncTaskResponse status(Long taskId);

    AsyncTaskResponse cancel(Long taskId);

    /**
     * 服务启动时恢复所有未完成的任务（PENDING / PROCESSING）。
     */
    void recoverUnfinishedTasks();

    /**
     * 分页查询任务列表，支持按状态过滤。
     */
    Page<AsyncTaskResponse> list(TaskState state, int page, int size);

    /**
     * 分页获取未确认的任务（活动的 + 已完成但未读的）。
     */
    Page<AsyncTaskResponse> unacknowledged(int page, int size);

    /**
     * 标记单个任务为已确认。
     */
    void acknowledge(Long taskId);

    /**
     * 批量标记所有已完成（COMPLETED/FAILED/CANCELLED）的未确认任务为已确认。
     */
    void acknowledgeAllCompleted();

    /**
     * 将正在进行的批量翻译任务切换为即时逐条翻译。
     * 取消 Gemini Batch Job，清除 batchJobName，从头以直接 API 调用方式重新翻译。
     */
    AsyncTaskResponse switchToDirectTranslate(Long taskId);

    @Async("threadPoolTaskExecutor")
    void executeDirectTranslateAsync(Long taskId);

    /**
     * 重试失败/已取消的任务。
     */
    AsyncTaskResponse retry(Long taskId);
}
