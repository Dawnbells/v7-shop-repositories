package cn.v7soft.admin.service;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;

import java.io.InputStream;

public interface IAsyncTaskService extends IBaseDataRangeService<AsyncTask> {

    boolean updateAsyncTask(AsyncTask task, TaskState taskState, int progress);

    Pair<AsyncTask, SystemUserDto> getAndInitializeOwner(Long taskId);

    AsyncTaskResponse status(Long taskId);

    AsyncTaskResponse cancel(Long taskId);

    InputStream download(Long id);

    void acknowledge(Long taskId);

    void acknowledgeAllCompleted();

    AsyncTaskResponse retry(Long taskId);

    boolean finalizeBilling(Long taskId);
}
