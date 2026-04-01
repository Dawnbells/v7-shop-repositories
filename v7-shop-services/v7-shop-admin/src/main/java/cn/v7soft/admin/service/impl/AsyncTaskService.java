package cn.v7soft.admin.service.impl;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AsyncTaskService extends BaseService<AsyncTask, AsyncTaskRepository> implements IAsyncTaskService {

    public AsyncTaskService(AsyncTaskRepository repository) {
        super(repository);
    }

    @Override
    public void executeOrderDownload(AsyncTask task) {

    }

    @Override
    @Transactional
    public Pair<AsyncTask, SystemUserDto> getAndInitializeOwner(Long taskId) {
        AsyncTask asyncTask = getById(taskId);
        SystemUserDto owner = SystemUserDto.convert(asyncTask.getOwner());
        return new Pair<>(asyncTask, owner);
    }

    @Override
    @Transactional
    public void updateAsyncTask(AsyncTask task, TaskState state, int progress) {
        TaskState current = task.getState();
        if (!current.canTransitionTo(state)) {
            log.warn("[updateAsyncTask] 非法状态迁移被拦截: taskId={}, {} -> {}", task.getId(), current, state);
            return;
        }
        log.debug("update async task >> {} >> {} -> {} >> {} ", task.getId(), current, state, progress);
        task.setState(state);
        task.setProgress(progress);
        saveAndFlush(task);
    }

}
