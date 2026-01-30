package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.ITaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final ITaskService taskService;
    @GetMapping("/status/{taskId}")
    public AsyncTaskResponse status(@PathVariable("taskId") Long taskId) {
        return taskService.status(taskId);
    }
}
