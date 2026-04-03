package cn.v7soft.admin.controller.req;

import java.util.List;

import cn.v7soft.core.controller.request.BasePageRequest;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "异步任务分页查询请求")
public class QueryAsyncTaskRequest extends BasePageRequest {

    @Schema(title = "任务状态过滤")
    private TaskState state;

    @Schema(title = "是否只查未确认任务")
    private Boolean unacknowledgedOnly;

    @Schema(title = "任务类型过滤（多选）")
    private List<TaskType> taskTypes;
}
