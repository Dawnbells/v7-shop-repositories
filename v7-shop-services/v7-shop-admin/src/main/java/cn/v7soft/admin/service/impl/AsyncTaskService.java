package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.BatchJob;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AsyncTaskService extends BaseDataRangeService<AsyncTask, AsyncTaskRepository> implements IAsyncTaskService {

    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AsyncTaskRepository asyncTaskRepository;
    private final IS3Service s3Service;
    private final GeminiTranslateService geminiTranslateService;
    private final ITaskExecutorService taskExecutorService;

    public AsyncTaskService(AsyncTaskRepository repository,
                            IS3Service s3Service,
                            GeminiTranslateService geminiTranslateService,
                            @Lazy ITaskExecutorService taskExecutorService) {
        super(repository);
        this.asyncTaskRepository = repository;
        this.s3Service = s3Service;
        this.geminiTranslateService = geminiTranslateService;
        this.taskExecutorService = taskExecutorService;
    }

    @Override
    public QueryAttribute getAccessDataRangeQueryAttribute() {
        return new AccessDataRangeAttribute(AccessDataRangeLevel.PERSON);
    }

    @Override
    @Transactional
    public Pair<AsyncTask, SystemUserDto> getAndInitializeOwner(Long taskId) {
        AsyncTask asyncTask = getById(taskId);
        SystemUserDto owner = SystemUserDto.convert(asyncTask.getOwner());
        return new Pair<>(asyncTask, owner);
    }

    @Override
    @Transactional
    public void updateAsyncTask(AsyncTask task, TaskState state, int progress) {
        AsyncTask fresh = getById(task.getId());
        TaskState current = fresh.getState();
        if (!current.canTransitionTo(state)) {
            log.warn("[updateAsyncTask] 非法状态迁移被拦截: taskId={}, {} -> {}", task.getId(), current, state);
            return;
        }
        log.debug("update async task >> {} >> {} -> {} >> {} ", task.getId(), current, state, progress);
        fresh.setState(state);
        fresh.setProgress(progress);
        fresh.setMessage(task.getMessage());
        if (task.getBatchJobName() != null) {
            fresh.setBatchJobName(task.getBatchJobName());
        }
        if (task.getParameters() != null) {
            fresh.setParameters(task.getParameters());
        }
        if (task.getTaskType() != null) {
            fresh.setTaskType(task.getTaskType());
        }
        if (task.getExportRelativePath() != null) {
            fresh.setExportRelativePath(task.getExportRelativePath());
        }
        saveAndFlush(fresh);
    }

    @Override
    public AsyncTaskResponse status(Long taskId) {
        AsyncTask task = getById(taskId);
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public AsyncTaskResponse cancel(Long taskId) {
        AsyncTask task = getById(taskId);
        log.info("[cancel] taskId={} 请求取消, 当前状态={}, taskType={}, batchJobName={}",
                taskId, task.getState(), task.getTaskType(), task.getBatchJobName());
        if (task.getState() == TaskState.PENDING || task.getState() == TaskState.PROCESSING) {
            task.setMessage("任务已取消");
            updateAsyncTask(task, TaskState.CANCELLED, COMPLETED_OR_FAILED_PROGRESS);
            if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                try {
                    geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                    geminiTranslateService.deleteBatchJob(task.getBatchJobName());
                    log.info("[cancel] taskId={} Gemini Batch 资源已清理", taskId);
                } catch (Exception e) {
                    log.warn("[cancel] taskId={} 清理 Gemini Batch 资源失败: {}", taskId, e.getMessage());
                }
            }
        } else {
            log.info("[cancel] taskId={} 状态为 {}, 不可取消", taskId, task.getState());
        }
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public InputStream download(Long id) {
        AsyncTask task = getById(id);
        return s3Service.download(task.getExportRelativePath());
    }

    @Override
    public void acknowledge(Long taskId) {
        AsyncTask task = getById(taskId);
        task.setAcknowledged(true);
        asyncTaskRepository.saveAndFlush(task);
    }

    @Override
    public void acknowledgeAllCompleted() {
        List<AsyncTask> unacked = asyncTaskRepository.findByAcknowledgedFalseOrderByCreateTimeDesc();
        for (AsyncTask task : unacked) {
            if (task.getState() == TaskState.COMPLETED || task.getState() == TaskState.FAILED || task.getState() == TaskState.CANCELLED) {
                task.setAcknowledged(true);
            }
        }
        asyncTaskRepository.saveAllAndFlush(unacked);
    }

    @Override
    @Transactional
    public AsyncTaskResponse switchToDirectTranslate(Long taskId) {
        AsyncTask task = getById(taskId);
        log.info("[switchToDirectTranslate] taskId={} 请求切换, 当前状态={}, batchJobName={}",
                taskId, task.getState(), task.getBatchJobName());

        if (task.getTaskType() != TaskType.PRODUCT_AI_TRANSLATE) {
            throw new IllegalArgumentException("只有批量 AI 翻译任务支持此操作");
        }
        if (task.getState() != TaskState.PROCESSING && task.getState() != TaskState.PENDING) {
            throw new IllegalStateException("只有执行中或等待中的任务才能切换模式");
        }

        if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
            BatchJob batchJob = geminiTranslateService.getBatchJob(task.getBatchJobName());
            try {
                JsonNode jobJson = OBJECT_MAPPER.readTree(batchJob.toJson());
                JsonNode statsNode = jobJson.path("batchStats");
                if (!statsNode.isMissingNode()) {
                    long requestCount = Long.parseLong(statsNode.path("requestCount").asText("0"));
                    long pendingCount = Long.parseLong(statsNode.path("pendingRequestCount").asText("0"));
                    if (pendingCount < requestCount) {
                        throw new IllegalStateException(
                                "批量任务已有部分请求在处理中，不允许切换为即时翻译 (pending="
                                        + pendingCount + "/" + requestCount + ")");
                    }
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[switchToDirectTranslate] taskId={} 读取 batchStats 失败, 继续切换", task.getId(), e);
            }
            try {
                geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                geminiTranslateService.deleteBatchJob(task.getBatchJobName());
            } catch (Exception e) {
                log.warn("[switchToDirectTranslate] taskId={} 清理 Batch Job 失败: {}", taskId, e.getMessage());
            }
        }

        task.setBatchJobName(null);
        task.setTaskType(TaskType.PRODUCT_AI_TRANSLATE_DIRECT);
        task.setMessage("正在切换为即时翻译...");
        updateAsyncTask(task, TaskState.PENDING, 0);
        asyncTaskRepository.resetCreateTime(taskId, LocalDateTime.now());
        log.info("[switchToDirectTranslate] taskId={} 已重置为 PENDING (DIRECT), 启动即时翻译", taskId);
        taskExecutorService.submitAsyncTask(task.getId());
        task.setCreateTime(LocalDateTime.now());
        return AsyncTaskResponse.convert(task);
    }

    @Override
    @Transactional
    public AsyncTaskResponse retry(Long taskId) {
        AsyncTask task = getById(taskId);
        log.info("[retry] taskId={} 请求重试, 当前状态={}, taskType={}", taskId, task.getState(), task.getTaskType());
        if (task.getState() != TaskState.FAILED && task.getState() != TaskState.CANCELLED) {
            throw new IllegalStateException("只有失败或已取消的任务才能重试");
        }
        task.setBatchJobName(null);
        task.setMessage("正在重试...");
        task.setAcknowledged(false);
        updateAsyncTask(task, TaskState.PENDING, 0);
        asyncTaskRepository.resetCreateTime(taskId, LocalDateTime.now());
        log.info("[retry] taskId={} 已重置为 PENDING, 重新提交任务", taskId);
        taskExecutorService.submitAsyncTask(task.getId());
        task.setCreateTime(LocalDateTime.now());
        return AsyncTaskResponse.convert(task);
    }
}
