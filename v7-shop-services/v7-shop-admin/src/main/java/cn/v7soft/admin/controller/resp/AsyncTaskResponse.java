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

    private Long taskId;

    private TaskType taskType;

    private TaskState state;

    private Integer progress;

    private String message;

    public static AsyncTaskResponse convert(AsyncTask task) {
        return AsyncTaskResponse.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .state(task.getState())
                .progress(task.getProgress())
                .message(task.getMessage())
                .build();
    }
}
