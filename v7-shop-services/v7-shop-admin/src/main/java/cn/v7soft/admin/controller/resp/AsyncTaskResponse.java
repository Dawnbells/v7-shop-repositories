package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import jakarta.persistence.Column;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncTaskResponse {
    /**
     * 任务类型（如订单下载、内容下载等）
     */
    private TaskType taskType;

    /**
     * 任务状态（如 PENDING, RUNNING, COMPLETED, FAILED）
     */
    private TaskState state;

    /**
     * 任务进度（0-100）
     */
    private Integer progress;
    /**
     * 结果说明或错误消息
     */
    @Column(name = "message", length = 500)
    private String message;

    public static AsyncTaskResponse convert(AsyncTask task) {
        return AsyncTaskResponse.builder()
                .taskType(task.getTaskType())
                .state(task.getState())
                .progress(task.getProgress())
                .message(task.getMessage())
                .build();
    }
}
