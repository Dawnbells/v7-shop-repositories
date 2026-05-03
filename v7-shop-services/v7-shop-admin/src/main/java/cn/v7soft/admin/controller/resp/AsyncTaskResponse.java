package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Schema(description = "异步任务响应")
public class AsyncTaskResponse extends DataRangeResponse {

    @Schema(title = "任务名称")
    private String name;

    @Schema(title = "任务类型")
    private TaskType taskType;

    @Schema(title = "任务状态")
    private TaskState state;

    @Schema(title = "进度(0-100)")
    private Integer progress;

    @Schema(title = "消息")
    private String message;

    @Schema(title = "创建时间")
    private LocalDateTime createTime;

    @Schema(title = "是否有下载文件")
    private boolean hasDownload;

    @Schema(title = "是否已确认")
    private boolean acknowledged;

    @Schema(title = "是否批量模式")
    private boolean inBatchMode;

    @Schema(title = "记录数")
    private Long recordCount;

    @Schema(title = "总输入Token")
    private Integer totalPromptTokens;

    @Schema(title = "总输出Token")
    private Integer totalCompletionTokens;

    @Schema(title = "总思考Token")
    private Integer totalThinkingTokens;

    @Schema(title = "消耗Credits")
    private Integer totalBusinessCredits;

    @Schema(title = "是否已结算")
    private Boolean billingSettled;

    @Schema(title = "结算时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime billingSettledAt;

    public static AsyncTaskResponse convert(AsyncTask task) {
        boolean isBatch = task.getBatchJobName() != null && !task.getBatchJobName().isBlank();
        return filling(task, AsyncTaskResponse.builder()
                .name(task.getName())
                .taskType(task.getTaskType())
                .state(task.getState())
                .progress(task.getProgress())
                .message(task.getMessage())
                .createTime(task.getCreateTime())
                .hasDownload(task.getExportRelativePath() != null && !task.getExportRelativePath().isBlank())
                .acknowledged(Boolean.TRUE.equals(task.getAcknowledged()))
                .inBatchMode(isBatch)
                .recordCount(task.getBillingRecordCount())
                .totalPromptTokens(task.getBillingTotalPromptTokens())
                .totalCompletionTokens(task.getBillingTotalCompletionTokens())
                .totalThinkingTokens(task.getBillingTotalThinkingTokens())
                .totalBusinessCredits(task.getBillingActualCredits())
                .billingSettled(task.getBillingSettled())
                .billingSettledAt(task.getBillingSettledAt())
                .build());
    }
}
