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
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AsyncTaskService extends BaseDataRangeService<AsyncTask, AsyncTaskRepository> implements IAsyncTaskService {

    private static final int COMPLETED_OR_FAILED_PROGRESS = 100;

    private final AsyncTaskRepository asyncTaskRepository;
    private final IS3Service s3Service;
    private final ITaskExecutorService taskExecutorService;
    private final AiCreditsService aiCreditsService;
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;

    public AsyncTaskService(AsyncTaskRepository repository,
                            IS3Service s3Service,
                            @Lazy ITaskExecutorService taskExecutorService,
                            AiCreditsService aiCreditsService,
                            AiTokenUsageRecordRepository aiTokenUsageRecordRepository) {
        super(repository);
        this.asyncTaskRepository = repository;
        this.s3Service = s3Service;
        this.taskExecutorService = taskExecutorService;
        this.aiCreditsService = aiCreditsService;
        this.aiTokenUsageRecordRepository = aiTokenUsageRecordRepository;
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
    public boolean updateAsyncTask(AsyncTask task, TaskState state, int progress) {
        AsyncTask fresh = getById(task.getId());
        TaskState current = fresh.getState();
        if (!current.canTransitionTo(state)) {
            log.debug("[updateAsyncTask] 状态迁移跳过: taskId={}, {} -> {}", task.getId(), current, state);
            return false;
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
        if (task.getEstimatedCredits() != null && fresh.getEstimatedCredits() == null) {
            fresh.setEstimatedCredits(task.getEstimatedCredits());
        }

        saveAndFlush(fresh);
        return true;
    }

    private boolean isTerminalState(TaskState state) {
        return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
    }

    @Override
    @Transactional
    public synchronized boolean finalizeBilling(Long taskId) {
        AsyncTask task = getById(taskId);
        if (!isTerminalState(task.getState())) {
            log.debug("[finalizeBilling] taskId={} 尚未终态, 跳过结算", taskId);
            return false;
        }
        if (Boolean.TRUE.equals(task.getBillingSettled())) {
            log.debug("[finalizeBilling] taskId={} 已结算, 跳过", taskId);
            return false;
        }

        int actualCredits = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int totalThinkingTokens = 0;
        long recordCount = aiTokenUsageRecordRepository.countByTaskId(taskId);
        if (recordCount > 0) {
            actualCredits = aiTokenUsageRecordRepository.sumBusinessCreditsByTaskId(taskId);
            totalPromptTokens = aiTokenUsageRecordRepository.sumBusinessPromptTokensByTaskId(taskId);
            totalCompletionTokens = aiTokenUsageRecordRepository.sumBusinessCompletionTokensByTaskId(taskId);
            totalThinkingTokens = aiTokenUsageRecordRepository.sumBusinessThinkingTokensByTaskId(taskId);
        }

        if (task.getEstimatedCredits() != null) {
            Long ownerId = task.getOwner().getId();
            if (actualCredits > 0) {
                aiCreditsService.settle(ownerId, task.getEstimatedCredits(), actualCredits);
            } else {
                aiCreditsService.unfreeze(ownerId, task.getEstimatedCredits());
            }
        }

        task.setBillingRecordCount(recordCount);
        task.setBillingActualCredits(actualCredits);
        task.setBillingTotalPromptTokens(totalPromptTokens);
        task.setBillingTotalCompletionTokens(totalCompletionTokens);
        task.setBillingTotalThinkingTokens(totalThinkingTokens);
        task.setBillingSettled(true);
        task.setBillingSettledAt(LocalDateTime.now());
        saveAndFlush(task);
        log.info("[finalizeBilling] taskId={} 结算完成: estimated={}, actual={}, records={}, promptTokens={}, completionTokens={}, thinkingTokens={}",
                taskId, task.getEstimatedCredits(), actualCredits, recordCount, totalPromptTokens, totalCompletionTokens, totalThinkingTokens);
        return true;
    }

    @Override
    public AsyncTaskResponse status(Long taskId) {
        AsyncTask task = getById(taskId);
        return AsyncTaskResponse.convert(task);
    }

    @Override
    public AsyncTaskResponse cancel(Long taskId) {
        AsyncTask task = getById(taskId);
        TaskState originalState = task.getState();
        log.info("[cancel] taskId={} 请求取消, 当前状态={}, taskType={}",
                taskId, task.getState(), task.getTaskType());
        if (task.getState() == TaskState.PENDING || task.getState() == TaskState.PROCESSING) {
            task.setMessage("任务已取消");
            updateAsyncTask(task, TaskState.CANCELLED, COMPLETED_OR_FAILED_PROGRESS);
            if (task.getTaskType() == TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE || originalState == TaskState.PENDING) {
                finalizeBilling(task.getId());
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
    public AsyncTaskResponse retry(Long taskId) {
        AsyncTask oldTask = getById(taskId);
        log.info("[retry] taskId={} 请求重试, 当前状态={}, taskType={}", taskId, oldTask.getState(), oldTask.getTaskType());
        if (oldTask.getState() != TaskState.FAILED
                && oldTask.getState() != TaskState.CANCELLED
                && oldTask.getState() != TaskState.INSUFFICIENT_CREDITS) {
            throw new IllegalStateException("只有失败、已取消或积分不足的任务才能重试");
        }

        Integer estimated = oldTask.getEstimatedCredits();
        if (estimated != null && oldTask.getState() != TaskState.INSUFFICIENT_CREDITS) {
            aiCreditsService.freeze(oldTask.getOwner().getId(), estimated);
        }

        oldTask.setAcknowledged(true);
        asyncTaskRepository.save(oldTask);

        String newName = oldTask.getName();
        if (newName != null && !newName.startsWith("（重试）")) {
            newName = "（重试）" + newName;
        }

        AsyncTask newTask = AsyncTask.builder()
                .taskType(oldTask.getTaskType())
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(oldTask.getParameters())
                .name(newName)
                .dedupKey(oldTask.getDedupKey())
                .estimatedCredits(estimated)
                .build();
        newTask.setOwner(oldTask.getOwner());
        newTask.setCompanyId(oldTask.getCompanyId());
        newTask = asyncTaskRepository.saveAndFlush(newTask);

        log.info("[retry] oldTaskId={} -> newTaskId={}, estimatedCredits={}", taskId, newTask.getId(), estimated);
        if (newTask.getTaskType() != TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE) {
            taskExecutorService.submitAsyncTask(newTask.getId());
        }
        return AsyncTaskResponse.convert(newTask);
    }
}
