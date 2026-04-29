package cn.v7soft.dao.repositories.primary;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cn.v7soft.core.repository.BaseRepository;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;

public interface AsyncTaskRepository extends BaseRepository<AsyncTask> {

    @Modifying
    @Query("UPDATE AsyncTask t SET t.createTime = :time WHERE t.id = :id")
    void resetCreateTime(@Param("id") Long id, @Param("time") LocalDateTime time);

    List<AsyncTask> findByTaskTypeAndParametersAndStateIn(TaskType taskType, String parameters, List<TaskState> states);

    List<AsyncTask> findByTaskTypeAndDedupKeyAndStateIn(TaskType taskType, String dedupKey, List<TaskState> states);

    List<AsyncTask> findByTaskTypeAndStateOrderByCreateTimeAsc(TaskType taskType, TaskState state, Pageable pageable);

    List<AsyncTask> findByStateIn(List<TaskState> states);

    Page<AsyncTask> findByStateOrderByCreateTimeDesc(TaskState state, Pageable pageable);

    Page<AsyncTask> findAllByOrderByCreateTimeDesc(Pageable pageable);

    List<AsyncTask> findByAcknowledgedFalseOrderByCreateTimeDesc();

    Page<AsyncTask> findByAcknowledgedFalseOrderByCreateTimeDesc(Pageable pageable);
}
