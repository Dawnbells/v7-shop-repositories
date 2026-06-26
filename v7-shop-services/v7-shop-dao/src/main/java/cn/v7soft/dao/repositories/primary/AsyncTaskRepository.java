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

    /**
     * 仅取任务归属用户 id（引用 user_id 外键列，不 JOIN、不初始化 owner 关联），
     * 用于下载归属校验，避免懒加载/OSIV 依赖。任务不存在或 owner 为空时返回 null。
     */
    @Query("SELECT t.owner.id FROM AsyncTask t WHERE t.id = :id")
    Long findOwnerIdById(@Param("id") Long id);

    List<AsyncTask> findByTaskTypeAndParametersAndStateIn(TaskType taskType, String parameters, List<TaskState> states);

    List<AsyncTask> findByTaskTypeAndDedupKeyAndStateIn(TaskType taskType, String dedupKey, List<TaskState> states);

    List<AsyncTask> findByTaskTypeAndStateOrderByCreateTimeAsc(TaskType taskType, TaskState state, Pageable pageable);

    List<AsyncTask> findByTaskTypeAndState(TaskType taskType, TaskState state);

    List<AsyncTask> findByStateIn(List<TaskState> states);

    Page<AsyncTask> findByStateOrderByCreateTimeDesc(TaskState state, Pageable pageable);

    Page<AsyncTask> findAllByOrderByCreateTimeDesc(Pageable pageable);

    List<AsyncTask> findByAcknowledgedFalseOrderByCreateTimeDesc();

    Page<AsyncTask> findByAcknowledgedFalseOrderByCreateTimeDesc(Pageable pageable);
}
