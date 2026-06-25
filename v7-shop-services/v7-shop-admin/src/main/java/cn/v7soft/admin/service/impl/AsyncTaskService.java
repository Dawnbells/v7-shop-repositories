package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.lang.Pair;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.resp.AsyncTaskResponse;
import cn.v7soft.admin.event.AiTranslateTaskNotificationEvent;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ITaskExecutorService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.common.controller.req.attributes.AccessDataRangeAttribute;
import cn.v7soft.common.enums.AccessDataRangeLevel;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.core.controller.request.attributes.QueryAttribute;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.repositories.primary.AiAccountRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.CountryRepository;
import cn.v7soft.dao.repositories.primary.LanguageRepository;
import cn.v7soft.dao.repositories.primary.ProductRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
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
    private final ProductRepository productRepository;
    private final CountryRepository countryRepository;
    private final LanguageRepository languageRepository;
    private final AiAccountRepository aiAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderStatisticsExportDownloadGuard statisticsExportDownloadGuard;

    public AsyncTaskService(AsyncTaskRepository repository,
                            IS3Service s3Service,
                            @Lazy ITaskExecutorService taskExecutorService,
                            AiCreditsService aiCreditsService,
                            AiTokenUsageRecordRepository aiTokenUsageRecordRepository,
                            ProductRepository productRepository,
                            CountryRepository countryRepository,
                            LanguageRepository languageRepository,
                            AiAccountRepository aiAccountRepository,
                            ApplicationEventPublisher eventPublisher,
                            OrderStatisticsExportDownloadGuard statisticsExportDownloadGuard) {
        super(repository);
        this.asyncTaskRepository = repository;
        this.s3Service = s3Service;
        this.taskExecutorService = taskExecutorService;
        this.aiCreditsService = aiCreditsService;
        this.aiTokenUsageRecordRepository = aiTokenUsageRecordRepository;
        this.productRepository = productRepository;
        this.countryRepository = countryRepository;
        this.languageRepository = languageRepository;
        this.aiAccountRepository = aiAccountRepository;
        this.eventPublisher = eventPublisher;
        this.statisticsExportDownloadGuard = statisticsExportDownloadGuard;
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
            // R3：AI 翻译 PROCESSING 状态可能存在 in-flight 子任务，
            // 由 AiAccountTranslateTask.syncTaskStatus 串起 onTaskCancelling → settleTask 顺序，
            // 确保 in-flight 子任务的 partial businessCredits 先入账再结算，避免漏扣。
            // PENDING 状态尚未 loadTask 无 in-flight，可立即结算。
            if (task.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE) {
                if (originalState == TaskState.PENDING) {
                    finalizeBilling(task.getId());
                }
            } else if (originalState == TaskState.PENDING) {
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
        statisticsExportDownloadGuard.validate(task);
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

        // R1：AI 翻译任务的冻结由新任务的 loadTask 阶段统一执行，retry 不再前置 freeze
        // 也不复制 estimatedCredits（loadTask 会重新精算并写入），避免双重冻结
        Integer estimated = oldTask.getEstimatedCredits();
        boolean isAiTranslate = oldTask.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE;
        if (estimated != null
                && oldTask.getState() != TaskState.INSUFFICIENT_CREDITS
                && !isAiTranslate) {
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
                .estimatedCredits(isAiTranslate ? null : estimated)
                .build();
        newTask.setOwner(oldTask.getOwner());
        newTask.setCompanyId(oldTask.getCompanyId());
        newTask = asyncTaskRepository.saveAndFlush(newTask);

        log.info("[retry] oldTaskId={} -> newTaskId={}, estimatedCredits={}", taskId, newTask.getId(), estimated);
        if (newTask.getTaskType() == TaskType.PRODUCT_AI_TRANSLATE) {
            publishAiTranslateRetryNotification(oldTask, newTask);
        } else {
            taskExecutorService.submitAsyncTask(newTask.getId());
        }
        return AsyncTaskResponse.convert(newTask);
    }

    private void publishAiTranslateRetryNotification(AsyncTask oldTask, AsyncTask newTask) {
        try {
            TranslateByAIRequest request = JSONUtil.toBean(oldTask.getParameters(), TranslateByAIRequest.class);
            Long productId = parseLongOrNull(request.getProductId());
            Long countryId = parseLongOrNull(request.getCountryId());
            Long languageId = parseLongOrNull(request.getLanguageId());
            Long aiAccountId = parseLongOrNull(request.getAiAccountId());

            Product product = findProduct(productId);
            Country country = findCountry(countryId);
            Language language = findLanguage(languageId);
            AiAccount account = findAiAccount(aiAccountId);

            eventPublisher.publishEvent(AiTranslateTaskNotificationEvent.retry(
                    newTask.getCompanyId(),
                    oldTask.getId(),
                    newTask.getId(),
                    resolveOperatorName(newTask),
                    resolveProductTitle(product, productId),
                    resolveCountryName(country, countryId),
                    resolveLanguageName(language, languageId),
                    resolveAiAccountName(account, aiAccountId),
                    resolveCreatedAt(newTask)
            ));
        } catch (Exception e) {
            log.warn("发布 AI 翻译重试通知事件失败: oldTaskId={}, newTaskId={}, error={}",
                    oldTask.getId(), newTask.getId(), e.getMessage());
        }
    }

    private Product findProduct(Long id) {
        return id == null ? null : productRepository.findById(id).orElse(null);
    }

    private Country findCountry(Long id) {
        return id == null ? null : countryRepository.findById(id).orElse(null);
    }

    private Language findLanguage(Long id) {
        return id == null ? null : languageRepository.findById(id).orElse(null);
    }

    private AiAccount findAiAccount(Long id) {
        return id == null ? null : aiAccountRepository.findById(id).orElse(null);
    }

    private String resolveOperatorName(AsyncTask task) {
        try {
            SystemUserDto loginUser = SaSessionUtil.getLoginUser();
            if (loginUser != null && StrUtil.isNotBlank(loginUser.getName())) {
                return loginUser.getName();
            }
        } catch (Exception ignored) {
            // ignore session lookup fallback
        }
        SystemUser owner = task.getOwner();
        if (owner != null && StrUtil.isNotBlank(owner.getName())) {
            return owner.getName();
        }
        return "未知用户";
    }

    private String resolveProductTitle(Product product, Long id) {
        if (product != null && StrUtil.isNotBlank(product.getTitle())) {
            return product.getTitle();
        }
        return id == null ? "-" : "商品#" + id;
    }

    private String resolveCountryName(Country country, Long id) {
        if (country != null && StrUtil.isNotBlank(country.getName())) {
            return country.getName();
        }
        return id == null ? "-" : "国家#" + id;
    }

    private String resolveLanguageName(Language language, Long id) {
        if (language != null && StrUtil.isNotBlank(language.getName())) {
            return language.getName();
        }
        return id == null ? "-" : "语言#" + id;
    }

    private String resolveAiAccountName(AiAccount account, Long id) {
        if (account != null && StrUtil.isNotBlank(account.getName())) {
            return account.getName();
        }
        return id == null ? "-" : "AI账号#" + id;
    }

    private LocalDateTime resolveCreatedAt(AsyncTask task) {
        return task.getCreateTime() == null ? LocalDateTime.now() : task.getCreateTime();
    }

    private Long parseLongOrNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
