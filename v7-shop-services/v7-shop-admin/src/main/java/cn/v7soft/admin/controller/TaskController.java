package cn.v7soft.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.ITaskService;
import cn.v7soft.dao.enums.TaskState;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final ITaskService taskService;

    @GetMapping("/status/{taskId}")
    public AsyncTaskResponse status(@PathVariable("taskId") Long taskId) {
        return taskService.status(taskId);
    }

    @PostMapping("/cancel/{taskId}")
    public AsyncTaskResponse cancel(@PathVariable("taskId") Long taskId) {
        return taskService.cancel(taskId);
    }

    @GetMapping("/list")
    public Page<AsyncTaskResponse> list(
            @RequestParam(required = false) TaskState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taskService.list(state, page, size);
    }

    @GetMapping("/unacknowledged")
    public Page<AsyncTaskResponse> unacknowledged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taskService.unacknowledged(page, size);
    }

    @PostMapping("/acknowledge/{taskId}")
    public void acknowledge(@PathVariable("taskId") Long taskId) {
        taskService.acknowledge(taskId);
    }

    @PostMapping("/acknowledge-all-completed")
    public void acknowledgeAllCompleted() {
        taskService.acknowledgeAllCompleted();
    }

    @PostMapping("/switch-to-direct/{taskId}")
    public AsyncTaskResponse switchToDirectTranslate(@PathVariable("taskId") Long taskId) {
        return taskService.switchToDirectTranslate(taskId);
    }
}
