package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.lang.Pair;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
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
    private final AiCreditsService aiCreditsService;
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;

    public AsyncTaskService(AsyncTaskRepository repository,
                            IS3Service s3Service,
                            GeminiTranslateService geminiTranslateService,
                            @Lazy ITaskExecutorService taskExecutorService,
                            AiCreditsService aiCreditsService,
                            AiTokenUsageRecordRepository aiTokenUsageRecordRepository) {
        super(repository);
        this.asyncTaskRepository = repository;
        this.s3Service = s3Service;
        this.geminiTranslateService = geminiTranslateService;
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
        if (task.getEstimatedCredits() == null) {
            return false;
        }
        if (!isTerminalState(task.getState())) {
            log.debug("[finalizeBilling] taskId={} 尚未终态, 跳过结算", taskId);
            return false;
        }
        if (Boolean.TRUE.equals(task.getBillingSettled())) {
            log.debug("[finalizeBilling] taskId={} 已结算, 跳过", taskId);
            return false;
        }

        int actualCredits = 0;
        Long ownerId = task.getOwner().getId();
        if (aiTokenUsageRecordRepository.existsByTaskId(taskId)) {
            actualCredits = aiTokenUsageRecordRepository.sumBusinessCreditsByTaskId(taskId);
            aiCreditsService.settle(ownerId, task.getEstimatedCredits(), actualCredits);
        } else {
            aiCreditsService.unfreeze(ownerId, task.getEstimatedCredits());
        }

        task.setBillingActualCredits(actualCredits);
        task.setBillingSettled(true);
        task.setBillingSettledAt(LocalDateTime.now());
        saveAndFlush(task);
        log.info("[finalizeBilling] taskId={} 结算完成: estimated={}, actual={}",
                taskId, task.getEstimatedCredits(), actualCredits);
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
        log.info("[cancel] taskId={} 请求取消, 当前状态={}, taskType={}, batchJobName={}",
                taskId, task.getState(), task.getTaskType(), task.getBatchJobName());
        if (task.getState() == TaskState.PENDING || task.getState() == TaskState.PROCESSING) {

            if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()
                    && !TaskExecutorService.ALL_CACHED_BATCH_JOB_NAME.equals(task.getBatchJobName())) {
                try {
                    BatchJob batchJob = geminiTranslateService.getBatchJob(task.getBatchJobName());
                    JsonNode jobJson = OBJECT_MAPPER.readTree(batchJob.toJson());
                    JsonNode statsNode = jobJson.path("batchStats");
                    long successCount = 0;
                    if (!statsNode.isMissingNode()) {
                        successCount = Long.parseLong(statsNode.path("successfulRequestCount").asText("0"));
                    }

                    if (successCount > 0 && task.getEstimatedCredits() != null) {
                        int totalRequests = parseTotalRequests(task);
                        int estimatedUsedCredits = (int) Math.ceil(
                                task.getEstimatedCredits() * (double) successCount / Math.max(totalRequests, 1));
                        saveCancelEstimateRecord(task, estimatedUsedCredits);
                        log.info("[cancel] taskId={} 已消耗估算: success={}/{}, credits={}",
                                taskId, successCount, totalRequests, estimatedUsedCredits);
                    }

                    geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                    geminiTranslateService.deleteBatchJob(task.getBatchJobName());
                    log.info("[cancel] taskId={} Gemini Batch 资源已清理", taskId);
                } catch (Exception e) {
                    log.warn("[cancel] taskId={} 查询/清理 Batch 资源失败: {}", taskId, e.getMessage());
                }
            } else if (task.getBatchJobName() != null && !task.getBatchJobName().isBlank()) {
                try {
                    geminiTranslateService.cancelBatchJob(task.getBatchJobName());
                    geminiTranslateService.deleteBatchJob(task.getBatchJobName());
                } catch (Exception ignored) {}
            }

            task.setMessage("任务已取消");
            updateAsyncTask(task, TaskState.CANCELLED, COMPLETED_OR_FAILED_PROGRESS);
            if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE
                    || task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE_DIRECT
                    || originalState == TaskState.PENDING) {
                finalizeBilling(task.getId());
            }
        } else {
            log.info("[cancel] taskId={} 状态为 {}, 不可取消", taskId, task.getState());
        }
        return AsyncTaskResponse.convert(task);
    }

    private int parseTotalRequests(AsyncTask task) {
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            return request.getTotalRequests() != null ? request.getTotalRequests() : 0;
        } catch (Exception e) {
            log.warn("[parseTotalRequests] taskId={} 解析失败", task.getId(), e);
            return 0;
        }
    }

    private void saveCancelEstimateRecord(AsyncTask task, int estimatedUsedCredits) {
        try {
            if (aiTokenUsageRecordRepository.existsByTaskIdAndContentHashAndTargetLanguage(
                    task.getId(), "CANCEL_ESTIMATE", "CANCEL")) {
                log.debug("[saveCancelEstimateRecord] taskId={} 已存在取消估算记录, 跳过", task.getId());
                return;
            }
            BigDecimal businessCost = new BigDecimal(estimatedUsedCredits).divide(
                    new BigDecimal("1000"), 6, java.math.RoundingMode.HALF_UP);
            AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                    .taskId(task.getId())
                    .contentType(TranslationContentType.TEXT)
                    .contentHash("CANCEL_ESTIMATE")
                    .targetLanguage("CANCEL")
                    .cacheHit(false)
                    .model("estimate")
                    .invokeMode(InvokeMode.BATCH)
                    .actualPromptTokens(0)
                    .actualCompletionTokens(0)
                    .actualThinkingTokens(0)
                    .actualTotalTokens(0)
                    .businessPromptTokens(0)
                    .businessCompletionTokens(0)
                    .businessThinkingTokens(0)
                    .businessTotalTokens(0)
                    .actualCost(BigDecimal.ZERO)
                    .businessCost(businessCost)
                    .businessCredits(estimatedUsedCredits)
                    .hasImageOutput(false)
                    .build();
            record.setOwner(task.getOwner());
            aiTokenUsageRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.debug("[saveCancelEstimateRecord] taskId={} 取消估算记录已存在(并发写入)", task.getId());
        } catch (Exception e) {
            log.warn("[saveCancelEstimateRecord] taskId={} 写入取消估算记录失败", task.getId(), e);
        }
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

        Integer origEstimated = task.getEstimatedCredits();
        task.setMessage("已切换为即时翻译");
        updateAsyncTask(task, TaskState.CANCELLED, COMPLETED_OR_FAILED_PROGRESS);
        finalizeBilling(task.getId());

        Integer newEstimated = null;
        if (origEstimated != null) {
            newEstimated = origEstimated * 2;
            aiCreditsService.freeze(task.getOwner().getId(), newEstimated);
        }

        String newName = task.getName();
        if (newName != null && !newName.startsWith("（转）")) {
            newName = "（转）" + newName;
        }

        AsyncTask newTask = AsyncTask.builder()
                .taskType(TaskType.PRODUCT_AI_TRANSLATE_DIRECT)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(task.getParameters())
                .name(newName)
                .dedupKey(task.getDedupKey())
                .estimatedCredits(newEstimated)
                .build();
        newTask.setOwner(task.getOwner());
        newTask.setCompanyId(task.getCompanyId());
        newTask = asyncTaskRepository.saveAndFlush(newTask);

        log.info("[switchToDirectTranslate] oldTaskId={} -> newTaskId={}, estimatedCredits={}",
                taskId, newTask.getId(), newEstimated);
        taskExecutorService.submitAsyncTask(newTask.getId());
        return AsyncTaskResponse.convert(newTask);
    }

    @Override
    @Transactional
    public AsyncTaskResponse retry(Long taskId) {
        AsyncTask oldTask = getById(taskId);
        log.info("[retry] taskId={} 请求重试, 当前状态={}, taskType={}", taskId, oldTask.getState(), oldTask.getTaskType());
        if (oldTask.getState() != TaskState.FAILED && oldTask.getState() != TaskState.CANCELLED) {
            throw new IllegalStateException("只有失败或已取消的任务才能重试");
        }

        Integer estimated = oldTask.getEstimatedCredits();
        if (estimated != null) {
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
        taskExecutorService.submitAsyncTask(newTask.getId());
        return AsyncTaskResponse.convert(newTask);
    }
}
