package cn.v7soft.dao.repositories.primary;

import java.util.List;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;

public interface AsyncTaskRepository extends BaseRepository<AsyncTask> {

    List<AsyncTask> findByTaskTypeAndParametersAndStateIn(TaskType taskType, String parameters, List<TaskState> states);
}
