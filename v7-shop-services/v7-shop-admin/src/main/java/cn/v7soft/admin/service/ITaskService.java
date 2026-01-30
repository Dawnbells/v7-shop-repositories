package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.resp.AsyncTaskResponse;

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
}
