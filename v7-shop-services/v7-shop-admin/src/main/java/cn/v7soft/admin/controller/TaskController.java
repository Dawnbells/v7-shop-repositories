package cn.v7soft.admin.controller;

import org.jetbrains.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import cn.v7soft.admin.controller.req.EditAsyncTaskRequest;
import cn.v7soft.admin.controller.req.QueryAsyncTaskRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.common.controller.BaseDataRangeController;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.core.controller.request.QueryPageRequest;
import cn.v7soft.core.controller.request.attributes.EqualsQueryAttribute;
import cn.v7soft.core.controller.request.attributes.InAttribute;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/tasks")
@Tag(name = "异步任务管理")
public class TaskController extends BaseDataRangeController<AsyncTask, IAsyncTaskService, AsyncTaskResponse, QueryAsyncTaskRequest, EditAsyncTaskRequest> {

    protected TaskController(IAsyncTaskService service) {
        super(service);
    }

    @Override
    protected QueryPageRequest<AsyncTask> convertQueryPageRequest(QueryAsyncTaskRequest request) {
        if (cn.hutool.core.util.StrUtil.isBlank(request.getSortBy())) {
            request.setSortBy("createTime desc");
        }
        QueryPageRequest<AsyncTask> pageRequest = super.convertQueryPageRequest(request);
        pageRequest.addConstraint(request.getState() != null,
                EqualsQueryAttribute.builder().name("state").value(request.getState()).build());
        pageRequest.addConstraint(Boolean.TRUE.equals(request.getUnacknowledgedOnly()),
                EqualsQueryAttribute.builder().name("acknowledged").value(false).build());
        pageRequest.addConstraint(request.getTaskTypes() != null && !request.getTaskTypes().isEmpty(),
                InAttribute.<TaskType>builder().name("taskType").value(request.getTaskTypes()).build());
        return pageRequest;
    }

    @Override
    protected AsyncTaskResponse convertEntity(AsyncTask task) {
        return AsyncTaskResponse.convert(task);
    }

    @Override
    protected AsyncTask convertRequest(@Nullable AsyncTask dbEntity, EditAsyncTaskRequest request) {
        return AsyncTask.builder().build();
    }

    @Override
    protected String getPermissionPrefix() {
        return "tasks";
    }

    @GetMapping("/status/{taskId}")
    @Operation(summary = "查询任务状态")
    public AsyncTaskResponse status(@PathVariable("taskId") Long taskId) {
        return service.status(taskId);
    }

    @PostMapping("/cancel/{taskId}")
    @Operation(summary = "取消任务")
    public AsyncTaskResponse cancel(@PathVariable("taskId") Long taskId) {
        return service.cancel(taskId);
    }

    @PostMapping("/acknowledge/{taskId}")
    @Operation(summary = "确认任务")
    public void acknowledge(@PathVariable("taskId") Long taskId) {
        service.acknowledge(taskId);
    }

    @PostMapping("/acknowledge-all-completed")
    @Operation(summary = "确认所有已完成任务")
    public void acknowledgeAllCompleted() {
        service.acknowledgeAllCompleted();
    }

    @PostMapping("/switch-to-direct/{taskId}")
    @Operation(summary = "切换为即时翻译")
    public AsyncTaskResponse switchToDirectTranslate(@PathVariable("taskId") Long taskId) {
        return service.switchToDirectTranslate(taskId);
    }

    @PostMapping("/retry/{taskId}")
    @Operation(summary = "重试任务")
    public AsyncTaskResponse retry(@PathVariable("taskId") Long taskId) {
        return service.retry(taskId);
    }

    @Override
    protected AccessDataRangeLevel getPageAccessDataRangeLevel(QueryAsyncTaskRequest request) {
        return AccessDataRangeLevel.PERSON;
    }
}
