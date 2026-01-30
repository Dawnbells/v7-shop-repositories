package cn.v7soft.admin.service;

import cn.hutool.core.lang.Pair;
import cn.v7soft.core.service.IBaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;

public interface IAsyncTaskService extends IBaseService<AsyncTask>  {
    void executeOrderDownload(AsyncTask task);

    void updateAsyncTask(AsyncTask task, TaskState taskState, int progress);

    Pair<AsyncTask, SystemUserDto> getAndInitializeOwner(Long taskId);
}
