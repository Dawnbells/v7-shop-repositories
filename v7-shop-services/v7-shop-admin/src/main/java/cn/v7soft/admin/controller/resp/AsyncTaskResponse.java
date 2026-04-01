package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;

import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
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

    private LocalDateTime createTime;

    private boolean hasDownload;

    private boolean acknowledged;

    private boolean inBatchMode;

    public static AsyncTaskResponse convert(AsyncTask task) {
        boolean isBatch = task.getBatchJobName() != null && !task.getBatchJobName().isBlank();
        return AsyncTaskResponse.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .state(task.getState())
                .progress(task.getProgress())
                .message(task.getMessage())
                .createTime(task.getCreateTime())
                .hasDownload(task.getExportRelativePath() != null && !task.getExportRelativePath().isBlank())
                .acknowledged(Boolean.TRUE.equals(task.getAcknowledged()))
                .inBatchMode(isBatch)
                .build();
    }
}
