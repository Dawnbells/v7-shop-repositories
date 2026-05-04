package cn.v7soft.admin.task;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.impl.AiCreditsService;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.admin.task.provider.SubTaskResult;
import cn.v7soft.admin.task.provider.TranslateProvider;
import cn.v7soft.admin.task.provider.TranslateProviderCallback;
import cn.v7soft.admin.task.provider.TranslateTaskCallbackAdapter;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * AI 账号翻译任务编排器。
 * <p>
 * 完整翻译流程：
 * <pre>
 * 用户提交翻译请求 → AsyncTask(PENDING)
 *   ↓
 * [定时器一] executePendingTasks (5s)
 *   检查用户积分 → 不足标记 INSUFFICIENT_CREDITS
 *   积分充足 → loadTask:
 *     1. 从产品提取 TEXT/HTML/IMAGE 子任务
 *     2. 每个子任务估算积分 → 创建 AiTokenUsageRecord(frozenCredits)
 *     3. 子任务全部入队（按 AiAccount 分组的 FIFO 队列）
 *     4. 事务内批量保存 usage records + 冻结积分 + 标记 PROCESSING
 *   ↓
 * [定时器二] executeSubTasks (1s)
 *   遍历所有账号队列（优先失败队列）
 *   根据账号流控预留槽位 → provider.executeSubTask 分发
 *   TurboFlow: 子任务存入 Provider 内部队列，等待插件 poll
 *   ↓
 * [Provider 回调] onSubTaskCompleted / onSubTaskFailed
 *   ← TranslateTaskCallbackAdapter 中间类 →
 *   完成: updateUsageRecord（写入实际 token）→ 更新 TaskStatus
 *   失败: accumulateUsageRecord（累加 token）→ retryable 且 attemptCount < 3 入失败队列 / 否则标记 FAILED
 *   ↓
 * [定时器三] syncTaskStatus (5s)
 *   触发 Provider 回收过期 assignment
 *   同步内存态到 DB
 *   所有子任务结束 → finalizeAiAccountTranslateStatus（组装翻译产物）
 *   终态 → settleTask（SUM(businessCredits) → settle 解冻+扣实际）
 * </pre>
 * <p>
 * 实现 TranslateTaskContext 接口，供 TranslateTaskCallbackAdapter 回调时访问内部状态。
 */
@Slf4j
@Component
public class AiAccountTranslateTask implements TranslateTaskContext {

    /** 每轮 executePendingTasks 至多拆分的任务数。批量提交场景下 1 太慢（100 任务 ≈ 8 分钟才进执行阶段）。 */
    private static final int MAX_TASKS_PER_ROUND = 5;
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private final AsyncTaskRepository asyncTaskRepository;
    private final IProductService productService;
    private final IAiAccountService aiAccountService;
    private final ICompanyService companyService;
    private final IMultimediaFileService multimediaFileService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final AiTokenUsageRecordRepository usageRecordRepository;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final TextTranslationCacheRepository textTranslationCacheRepository;
    private final AiCreditsService aiCreditsService;
    private final TransactionTemplate transactionTemplate;
    private final List<TranslateProvider> providers;

    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> subTaskQueuesByAccount = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> failedSubTaskQueuesByAccount = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AiAccountTranslateTaskStatus> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AiAccountRuntimeState> accountRuntimeStates = new ConcurrentHashMap<>();
    private final AtomicBoolean loadingTasks = new AtomicBoolean(false);
    private final AtomicBoolean executingSubTasks = new AtomicBoolean(false);
    private final AtomicBoolean syncingTaskStatus = new AtomicBoolean(false);

    private Map<AiProvider, TranslateProvider> providerRegistry;

    public AiAccountTranslateTask(AsyncTaskRepository asyncTaskRepository,
                                  IProductService productService,
                                  IAiAccountService aiAccountService,
                                  ICompanyService companyService,
                                  IMultimediaFileService multimediaFileService,
                                  ILanguageService languageService,
                                  ICountryService countryService,
                                  AiTokenUsageRecordRepository usageRecordRepository,
                                  ImageTranslationCacheRepository imageTranslationCacheRepository,
                                  TextTranslationCacheRepository textTranslationCacheRepository,
                                  AiCreditsService aiCreditsService,
                                  TransactionTemplate transactionTemplate,
                                  List<TranslateProvider> providers) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.productService = productService;
        this.aiAccountService = aiAccountService;
        this.companyService = companyService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.usageRecordRepository = usageRecordRepository;
        this.imageTranslationCacheRepository = imageTranslationCacheRepository;
        this.textTranslationCacheRepository = textTranslationCacheRepository;
        this.aiCreditsService = aiCreditsService;
        this.transactionTemplate = transactionTemplate;
        this.providers = providers;
    }

    /** 启动初始化：构建 Provider 注册表，注入回调适配器，重置残留 PROCESSING 任务 */
    @PostConstruct
    public void initialize() {
        TranslateProviderCallback callback = new TranslateTaskCallbackAdapter(this);
        providerRegistry = new ConcurrentHashMap<>();
        for (TranslateProvider provider : providers) {
            provider.setCallback(callback);
            providerRegistry.put(provider.getProviderType(), provider);
        }
        log.debug("[AiAccountTranslateTask] initialized providers: count={}, providers={}",
                providerRegistry.size(), providerRegistry.keySet());
        resetProcessingTasksOnStartup();
    }

    /** 服务重启时将残留的 PROCESSING 任务重置为 PENDING，依靠翻译缓存跳过已完成内容 */
    private void resetProcessingTasksOnStartup() {
        List<AsyncTask> processingTasks = asyncTaskRepository.findByTaskTypeAndState(
                TaskType.PRODUCT_AI_TRANSLATE,
                TaskState.PROCESSING);
        if (processingTasks.isEmpty()) {
            return;
        }
        for (AsyncTask task : processingTasks) {
            task.setState(TaskState.PENDING);
            task.setProgress(0);
            task.setMessage("Task reset after server restart");
        }
        asyncTaskRepository.saveAll(processingTasks);
        log.warn("[AiAccountTranslateTask] reset processing tasks to pending on startup: count={}",
                 processingTasks.size());
    }

    // --- TranslateTaskContext implementation ---

    @Override
    public AiAccountTranslateTaskStatus getTaskStatus(Long taskId) {
        return runningTasks.get(taskId);
    }

    @Override
    public AiAccountRuntimeState getOrCreateRuntimeState(Long aiAccountId) {
        return accountRuntimeStates.computeIfAbsent(aiAccountId, AiAccountRuntimeState::new);
    }

    @Override
    public void pushToFailedQueue(AiAccountTranslateSubTask subTask) {
        failedSubTaskQueuesByAccount
                .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                .offer(subTask);
        log.debug("[AiAccountTranslateTask] subtask pushed to failed retry queue: taskId={}, subTaskId={}, type={}, aiAccountId={}, attempt={}",
                subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), subTask.getAiAccountId(),
                subTask.getAttemptCount().get());
    }

    @Override
    public void pushToPendingQueue(AiAccountTranslateSubTask subTask) {
        subTaskQueuesByAccount
                .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                .offer(subTask);
        log.debug("[AiAccountTranslateTask] subtask pushed to pending queue: taskId={}, subTaskId={}, type={}, aiAccountId={}",
                subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), subTask.getAiAccountId());
    }

    /**
     * 子任务完成时，将 Provider 回传的实际 token 用量写入对应的 AiTokenUsageRecord。
     * 由 TranslateTaskCallbackAdapter.onSubTaskCompleted 调用。
     */
    @Override
    public void updateUsageRecord(AiAccountTranslateSubTask subTask, SubTaskResult result) {
        try {
            usageRecordRepository.findByTaskIdAndSubTaskId(subTask.getTaskId(), subTask.getSubTaskId())
                    .ifPresent(record -> {
                        record.setBusinessPromptTokens(result.getBusinessPromptTokens());
                        record.setBusinessCompletionTokens(result.getBusinessCompletionTokens());
                        record.setBusinessThinkingTokens(result.getBusinessThinkingTokens());
                        record.setBusinessTotalTokens(result.getBusinessPromptTokens()
                                + result.getBusinessCompletionTokens() + result.getBusinessThinkingTokens());
                        record.setActualPromptTokens(result.getActualPromptTokens());
                        record.setActualCompletionTokens(result.getActualCompletionTokens());
                        record.setActualThinkingTokens(result.getActualThinkingTokens());
                        record.setActualTotalTokens(result.getActualPromptTokens()
                                + result.getActualCompletionTokens() + result.getActualThinkingTokens());
                        record.setBusinessCredits(result.getBusinessCredits());
                        record.setElapsedMs(result.getElapsedMs());

                        // actualCost: 按 AiAccount 配置 + actual tokens 计算（适用所有 Provider）
                        // 注意：record.aiAccount 是 LAZY，回调发生在 Provider 异步线程无 Session，
                        // 此处必须显式按 ID 重新加载，避免 LazyInitializationException
                        if (!result.isCacheHit()) {
                            AiAccount acc = aiAccountService.getById(subTask.getAiAccountId());
                            if (acc != null) {
                                BigDecimal actualCost = TokenCostCalculator.calculateCost(
                                        record.getContentType(), acc,
                                        result.getActualPromptTokens(), result.getActualCompletionTokens(),
                                        result.getActualThinkingTokens());
                                record.setActualCost(actualCost);
                            }
                        }

                        if (result.getTranslatedFile() != null) {
                            record.setTranslatedImagePath(result.getTranslatedFile().getRelativePath());
                            record.setHasImageOutput(true);
                        }
                        // 图片子任务：记录原图路径
                        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
                            try {
                                MultimediaFile sf = subTask.resolveSourceFile(multimediaFileService);
                                record.setSourceImagePath(sf.getRelativePath());
                            } catch (Exception ignored) {}
                        }
                        if (result.getTranslatedText() != null) {
                            record.setTranslatedText(result.getTranslatedText());
                        }
                        if (result.getTranslatedHtml() != null) {
                            record.setTranslatedText(result.getTranslatedHtml());
                        }
                        if (result.isCacheHit()) {
                            record.setCacheHit(true);
                        }
                        record.setAttemptCount(subTask.getAttemptCount().get());
                        usageRecordRepository.save(record);
                        log.debug("[AiAccountTranslateTask] usage record updated: taskId={}, subTaskId={}, type={}, actualTokens={}, businessCredits={}, cacheHit={}",
                                subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                                record.getActualTotalTokens(), record.getBusinessCredits(), record.getCacheHit());
                    });
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] updateUsageRecord failed: subTaskId={}", subTask.getSubTaskId(), e);
        }
    }

    /**
     * 子任务失败/重试时，将本次尝试消耗的 token 累加到 AiTokenUsageRecord。
     * 跨重试累加，确保最终 businessCredits 反映所有尝试的真实消耗。
     * 由 TranslateTaskCallbackAdapter.onSubTaskFailed 调用。
     */
    @Override
    public void accumulateUsageRecord(AiAccountTranslateSubTask subTask, SubTaskResult partialResult) {
        try {
            usageRecordRepository.findByTaskIdAndSubTaskId(subTask.getTaskId(), subTask.getSubTaskId())
                    .ifPresent(record -> {
                        record.setActualPromptTokens(safeAdd(record.getActualPromptTokens(), partialResult.getActualPromptTokens()));
                        record.setActualCompletionTokens(safeAdd(record.getActualCompletionTokens(), partialResult.getActualCompletionTokens()));
                        record.setActualThinkingTokens(safeAdd(record.getActualThinkingTokens(), partialResult.getActualThinkingTokens()));
                        record.setActualTotalTokens(record.getActualPromptTokens()
                                + record.getActualCompletionTokens() + record.getActualThinkingTokens());
                        record.setBusinessPromptTokens(safeAdd(record.getBusinessPromptTokens(), partialResult.getBusinessPromptTokens()));
                        record.setBusinessCompletionTokens(safeAdd(record.getBusinessCompletionTokens(), partialResult.getBusinessCompletionTokens()));
                        record.setBusinessThinkingTokens(safeAdd(record.getBusinessThinkingTokens(), partialResult.getBusinessThinkingTokens()));
                        record.setBusinessTotalTokens(record.getBusinessPromptTokens()
                                + record.getBusinessCompletionTokens() + record.getBusinessThinkingTokens());
                        record.setBusinessCredits(safeAdd(record.getBusinessCredits(), partialResult.getBusinessCredits()));
                        record.setAttemptCount(subTask.getAttemptCount().get());
                        usageRecordRepository.save(record);
                        log.debug("[AiAccountTranslateTask] usage record accumulated after failure: taskId={}, subTaskId={}, type={}, actualTokens={}, businessCredits={}, attempt={}",
                                subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                                record.getActualTotalTokens(), record.getBusinessCredits(), record.getAttemptCount());
                    });
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] accumulateUsageRecord failed: subTaskId={}", subTask.getSubTaskId(), e);
        }
    }

    @Override
    public void saveTranslationCache(AiAccountTranslateSubTask subTask, SubTaskResult result) {
        try {
            AiAccountTranslateTaskStatus status = runningTasks.get(subTask.getTaskId());
            if (status == null || status.getLanguage() == null) return;
            Language language = status.getLanguage();

            if (subTask.getType() == AiAccountTranslateSubTaskType.TEXT && result.getTranslatedText() != null) {
                textTranslationCacheRepository.save(TextTranslationCache.builder()
                        .contentHash(subTask.getContentKey())
                        .language(language)
                        .contentType(TranslationContentType.TEXT)
                        .sourceText(subTask.getContent())
                        .translatedText(result.getTranslatedText())
                        .build());
                log.debug("[AiAccountTranslateTask] text translation cache saved: taskId={}, subTaskId={}, languageId={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), language.getId());
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.HTML && result.getTranslatedHtml() != null) {
                String src = subTask.getContent();
                textTranslationCacheRepository.save(TextTranslationCache.builder()
                        .contentHash(subTask.getContentKey())
                        .language(language)
                        .contentType(TranslationContentType.HTML)
                        .sourceText(src != null && src.length() > 65535 ? src.substring(0, 65535) : src)
                        .translatedText(result.getTranslatedHtml())
                        .build());
                log.debug("[AiAccountTranslateTask] html translation cache saved: taskId={}, subTaskId={}, languageId={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), language.getId());
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
                String imageHash = subTask.getImageHash();
                if (StrUtil.isNotBlank(imageHash)) {
                    MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
                    imageTranslationCacheRepository.save(ImageTranslationCache.builder()
                            .imageHash(imageHash)
                            .sourceFile(sourceFile)
                            .language(language)
                            .translatedFile(result.getTranslatedFile())
                            .skipped(result.getTranslatedFile() == null)
                            .build());
                    log.debug("[AiAccountTranslateTask] image translation cache saved: taskId={}, subTaskId={}, languageId={}, skipped={}",
                            subTask.getTaskId(), subTask.getSubTaskId(), language.getId(),
                            result.getTranslatedFile() == null);
                }
            }
        } catch (DataIntegrityViolationException e) {
            log.debug("[AiAccountTranslateTask] translation cache already exists: subTaskId={}", subTask.getSubTaskId());
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] saveTranslationCache failed: subTaskId={}", subTask.getSubTaskId(), e);
        }
    }

    private int safeAdd(Integer a, int b) {
        return (a == null ? 0 : a) + b;
    }

    // --- Scheduled timers ---

    /**
     * 定时器一：任务拆接。
     * 拉取 PENDING 的 AsyncTask，检查用户积分后拆分为子任务入队。
     * 积分不足直接标记 INSUFFICIENT_CREDITS，不做拆解。
     */
    @Scheduled(fixedDelay = 5 * 1000, initialDelay = 60 * 1000)
    public void executePendingTasks() {
        if (!loadingTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            List<AsyncTask> pendingTasks = asyncTaskRepository.findByTaskTypeAndStateOrderByCreateTimeAsc(
                    TaskType.PRODUCT_AI_TRANSLATE,
                    TaskState.PENDING,
                    PageRequest.of(0, MAX_TASKS_PER_ROUND));

            if (pendingTasks.isEmpty()) {
                return;
            }
            for (AsyncTask task : pendingTasks) {
                try {
                    processSinglePendingTask(task);
                } catch (Exception e) {
                    log.error("[AiAccountTranslateTask] processPendingTask failed: taskId={}",
                              task.getId(), e);
                }
            }
        } finally {
            loadingTasks.set(false);
        }
    }

    /** 处理单个 PENDING 任务：检查内存态/积分后调 loadTask 拆分。 */
    private void processSinglePendingTask(AsyncTask task) {
        log.debug("[AiAccountTranslateTask] pending translate task picked: taskId={}, ownerId={}",
                task.getId(), task.getOwner() == null ? null : task.getOwner().getId());
        if (runningTasks.containsKey(task.getId())) {
            task.setState(TaskState.PROCESSING);
            asyncTaskRepository.save(task);
            log.debug("[AiAccountTranslateTask] pending task already running, db state synced: taskId={}", task.getId());
            return;
        }
        if (!aiCreditsService.hasAvailableCredits(task.getOwner().getId())) {
            task.setState(TaskState.INSUFFICIENT_CREDITS);
            task.setMessage("AI积分不足，请充值后重试");
            asyncTaskRepository.save(task);
            log.info("[AiAccountTranslateTask] 积分不足，标记 INSUFFICIENT_CREDITS: taskId={}, userId={}",
                     task.getId(), task.getOwner().getId());
            return;
        }
        loadTask(task);
    }

    /** 定时器二：子任务分发。遍历所有账号队列（优先失败队列），通过 Provider 分发。 */
    @Scheduled(fixedDelay = 1000, initialDelay = 35 * 1000)
    public void executeSubTasks() {
        if (!executingSubTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            Set<Long> allAccountIds = new LinkedHashSet<>();
            allAccountIds.addAll(failedSubTaskQueuesByAccount.keySet());
            allAccountIds.addAll(subTaskQueuesByAccount.keySet());
            if (!allAccountIds.isEmpty()) {
                log.debug("[AiAccountTranslateTask] executeSubTasks round started: accountCount={}, accounts={}",
                        allAccountIds.size(), allAccountIds);
            }

            for (Long aiAccountId : allAccountIds) {
                executeAccountSubTasks(aiAccountId);
            }
        } finally {
            executingSubTasks.set(false);
        }
    }

    /**
     * 定时器三：状态同步与结算。
     * 1. 触发各 Provider 回收过期 assignment
     * 2. 同步内存态到 DB
     * 3. 所有子任务完成/失败时，汇总 usage records 的 businessCredits 并调 settle 结算
     */
    @Scheduled(fixedDelay = 5 * 1000, initialDelay = 40 * 1000)
    public void syncTaskStatus() {
        if (!syncingTaskStatus.compareAndSet(false, true)) {
            return;
        }
        try {
            for (TranslateProvider provider : providerRegistry.values()) {
                provider.reclaimExpiredAssignments();
            }
            if (!runningTasks.isEmpty()) {
                log.debug("[AiAccountTranslateTask] syncTaskStatus round started: runningTaskCount={}",
                        runningTasks.size());
            }
            for (AiAccountTranslateTaskStatus status : runningTasks.values()) {
                syncSingleTaskStatus(status);
            }
        } finally {
            syncingTaskStatus.set(false);
        }
    }

    // --- Sub-task execution ---

    /**
     * 对单个 AI 账号执行子任务分发。
     * <p>
     * 流程：
     * 1. 查询 AiAccount，找不到则将该账号下所有排队子任务标记失败
     * 2. 通过 providerRegistry 获取对应 Provider
     * 3. 合并失败队列 + 普通队列的总任务数
     * 4. 根据账号流控（CONCURRENCY/RPD_RPM）预留可执行槽位
     * 5. 优先从失败队列取任务，其次从普通队列（FIFO 顺序）
     * 6. 调用 provider.executeSubTask 分发（对于 TurboFlow，实际是存入 Provider 内部队列等待 poll）
     * 7. 队列清空且无 in-flight 任务时，清理该账号的运行时状态
     */
    private void executeAccountSubTasks(Long aiAccountId) {
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(aiAccountId, AiAccountRuntimeState::new);
        AiAccount account;
        try {
            account = aiAccountService.getById(aiAccountId);
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 获取AI账号失败: aiAccountId={}", aiAccountId, e);
            Queue<AiAccountTranslateSubTask> failedQueue = failedSubTaskQueuesByAccount.get(aiAccountId);
            Queue<AiAccountTranslateSubTask> pendingQueue = subTaskQueuesByAccount.get(aiAccountId);
            if (failedQueue != null) {
                failQueuedSubTasks(failedQueue, "AI账号不存在或不可用: " + aiAccountId);
            }
            if (pendingQueue != null) {
                failQueuedSubTasks(pendingQueue, "AI账号不存在或不可用: " + aiAccountId);
            }
            cleanupAccountState(aiAccountId, runtimeState);
            return;
        }

        TranslateProvider provider = providerRegistry.get(account.getProvider());
        if (provider == null) {
            log.warn("[AiAccountTranslateTask] 未找到对应的 Provider: provider={}", account.getProvider());
            return;
        }

        // 合并两个队列的待处理数，失败队列优先
        Queue<AiAccountTranslateSubTask> failedQueue = failedSubTaskQueuesByAccount.get(aiAccountId);
        Queue<AiAccountTranslateSubTask> pendingQueue = subTaskQueuesByAccount.get(aiAccountId);
        int totalPending = queueSize(failedQueue) + queueSize(pendingQueue);

        // 根据账号流控预留槽位（CONCURRENCY 模式限并发数，RPD_RPM 模式限日/分钟请求数）
        int executableCount = runtimeState.reserveSlots(account, totalPending);
        log.debug("[AiAccountTranslateTask] account dispatch planning: aiAccountId={}, provider={}, failedQueue={}, pendingQueue={}, totalPending={}, executable={}, inFlight={}",
                aiAccountId, account.getProvider(), queueSize(failedQueue), queueSize(pendingQueue),
                totalPending, executableCount, runtimeState.getInFlightCount());
        int unusedReservations = 0;
        for (int i = 0; i < executableCount; i++) {
            AiAccountTranslateSubTask subTask = pollFromQueues(failedQueue, pendingQueue);
            if (subTask == null) {
                unusedReservations++;
                continue;
            }
            // 分发前检查缓存，命中则直接完成，不调用 Provider
            AiAccountTranslateTaskStatus status = runningTasks.get(subTask.getTaskId());
            if (status != null && tryCompleteFromCache(status, subTask, account)) {
                log.debug("[AiAccountTranslateTask] subtask completed from cache before provider dispatch: taskId={}, subTaskId={}, type={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType());
                runtimeState.releaseFinishedSlot();
                continue;
            }
            log.debug("[AiAccountTranslateTask] dispatching subtask to provider: taskId={}, subTaskId={}, type={}, provider={}, aiAccountId={}, attempt={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), account.getProvider(),
                    aiAccountId, subTask.getAttemptCount().get() + 1);
            provider.executeSubTask(subTask);
        }
        if (unusedReservations > 0) {
            runtimeState.releaseUnusedReservations(unusedReservations);
        }
        cleanupAccountState(aiAccountId, runtimeState);
    }

    /** 优先从失败队列取任务，其次从普通队列（FIFO） */
    private AiAccountTranslateSubTask pollFromQueues(Queue<AiAccountTranslateSubTask> failedQueue,
                                                     Queue<AiAccountTranslateSubTask> pendingQueue) {
        AiAccountTranslateSubTask subTask = failedQueue == null ? null : failedQueue.poll();
        if (subTask != null) {
            return subTask;
        }
        return pendingQueue == null ? null : pendingQueue.poll();
    }

    private int queueSize(Queue<?> queue) {
        return queue == null ? 0 : queue.size();
    }

    /** 将队列中所有待处理子任务标记为失败（账号不可用等场景） */
    private void failQueuedSubTasks(Queue<AiAccountTranslateSubTask> queue, String message) {
        AiAccountTranslateSubTask subTask;
        while ((subTask = queue.poll()) != null) {
            AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
            if (taskStatus == null) continue;
            subTask.fail(message);
            taskStatus.failPendingSubTask(subTask, message);
        }
    }

    /** 队列清空且无 in-flight 任务时，移除该账号的运行时状态以释放内存 */
    private void cleanupAccountState(Long aiAccountId, AiAccountRuntimeState runtimeState) {
        Queue<AiAccountTranslateSubTask> pendingQueue = subTaskQueuesByAccount.get(aiAccountId);
        Queue<AiAccountTranslateSubTask> failedQueue = failedSubTaskQueuesByAccount.get(aiAccountId);
        boolean pendingEmpty = pendingQueue == null || pendingQueue.isEmpty();
        boolean failedEmpty = failedQueue == null || failedQueue.isEmpty();
        if (pendingEmpty && failedEmpty && runtimeState.getInFlightCount() == 0) {
            if (pendingQueue != null) {
                subTaskQueuesByAccount.remove(aiAccountId, pendingQueue);
            }
            if (failedQueue != null) {
                failedSubTaskQueuesByAccount.remove(aiAccountId, failedQueue);
            }
            accountRuntimeStates.remove(aiAccountId, runtimeState);
        }
    }

    // --- Task loading & splitting ---

    /**
     * 拆分 AsyncTask 为子任务，全部入队由 Provider 统一分发。
     * 1. 解析参数，构建子任务列表（TEXT/HTML/IMAGE）
     * 2. 每个子任务估算积分 → 创建 AiTokenUsageRecord（frozenCredits）→ 入队
     * 3. 汇总 totalEstimatedCredits → aiCreditsService.tryFreeze → 写入 AsyncTask.estimatedCredits
     * 缓存查询不在此阶段进行，由 Provider 在执行阶段（如 pollTask）检查缓存。
     */
    private void loadTask(AsyncTask task) {
        try {
            log.debug("[AiAccountTranslateTask] loadTask started: taskId={}, ownerId={}",
                    task.getId(), task.getOwner() == null ? null : task.getOwner().getId());
            // 0. 重启恢复：清理上次运行残留的 usage records，按已发生的实际消耗 settle
            //    （不能直接 unfreeze，否则重启前已经回调写入的 businessCredits 会被丢弃，公司白嫖 API 成本）
            List<AiTokenUsageRecord> staleRecords = usageRecordRepository.findByTaskId(task.getId());
            if (!staleRecords.isEmpty()) {
                final Integer prevFrozen = task.getEstimatedCredits();
                final int actualBilled = staleRecords.stream()
                        .map(AiTokenUsageRecord::getBusinessCredits)
                        .filter(c -> c != null && c > 0)
                        .mapToInt(Integer::intValue)
                        .sum();
                transactionTemplate.executeWithoutResult(txStatus -> {
                    if (prevFrozen != null && prevFrozen > 0) {
                        aiCreditsService.settle(task.getOwner().getId(), prevFrozen, actualBilled);
                        task.setEstimatedCredits(null);
                        asyncTaskRepository.save(task);
                    }
                    usageRecordRepository.deleteAll(staleRecords);
                });
                log.warn("[AiAccountTranslateTask] restart recovery: settled actualBilled={} credits, cleaned {} stale records for taskId={}",
                         actualBilled, staleRecords.size(), task.getId());
            }

            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);

            // 1. 构建子任务列表（TEXT/HTML/IMAGE），每个子任务共享同一 AiAccount
            List<AiAccountTranslateSubTask> subTasks = buildSubTasks(task.getId(), request);
            long textCount = subTasks.stream().filter(st -> st.getType() == AiAccountTranslateSubTaskType.TEXT).count();
            long htmlCount = subTasks.stream().filter(st -> st.getType() == AiAccountTranslateSubTaskType.HTML).count();
            long imageCount = subTasks.stream().filter(st -> st.getType() == AiAccountTranslateSubTaskType.IMAGE).count();
            log.debug("[AiAccountTranslateTask] subtask list built: taskId={}, productId={}, languageId={}, aiAccountId={}, total={}, text={}, html={}, image={}",
                    task.getId(), request.getProductId(), request.getLanguageId(), request.getAiAccountId(),
                    subTasks.size(), textCount, htmlCount, imageCount);

            // 查询一次 Language 和 AiAccount，后续所有子任务共用
            Language language = languageService.getById(Long.parseLong(request.getLanguageId()));
            Long aiAccountId = Long.parseLong(request.getAiAccountId());
            AiAccount account = aiAccountService.getById(aiAccountId);
            TranslateProvider provider = providerRegistry.get(account.getProvider());
            log.debug("[AiAccountTranslateTask] translate context resolved: taskId={}, aiAccountId={}, provider={}, language={}",
                    task.getId(), aiAccountId, account.getProvider(), language.getName());

            // 2. 创建内存态任务状态（productId/countryId 只存 ID，language 存实体）
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(
                    task.getId(), subTasks.size(),
                    Long.parseLong(request.getProductId()),
                    language,
                    Long.parseLong(request.getCountryId()),
                    task.getOwner());
            AiAccountTranslateTaskStatus existing = runningTasks.putIfAbsent(task.getId(), status);
            if (existing != null) {
                log.debug("[AiAccountTranslateTask] loadTask skipped because task already exists in memory: taskId={}",
                        task.getId());
                return;
            }

            if (subTasks.isEmpty()) {
                status.complete();
                log.debug("[AiAccountTranslateTask] loadTask completed without subtasks: taskId={}", task.getId());
                return;
            }

            // 3. 遍历子任务：估算积分、创建 usage record（不入队，等事务提交后再投递）
            int totalEstimatedCredits = 0;
            List<AiTokenUsageRecord> usageRecords = new ArrayList<>();

            for (AiAccountTranslateSubTask subTask : subTasks) {
                subTask.setOwner(task.getOwner());
                status.addSubTask(subTask);

                int estimated = estimateSubTaskCredits(provider, account, subTask);
                totalEstimatedCredits += estimated;
                log.debug("[AiAccountTranslateTask] subtask estimated: taskId={}, subTaskId={}, type={}, frozenCredits={}",
                        task.getId(), subTask.getSubTaskId(), subTask.getType(), estimated);

                AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                        .taskId(task.getId())
                        .subTaskId(subTask.getSubTaskId())
                        .aiAccount(account)
                        .contentType(mapContentType(subTask.getType()))
                        .contentHash(subTask.getContentKey())
                        .targetLanguage(language.getName())
                        .model(StrUtil.blankToDefault(account.getModel(), "turboflow"))
                        .frozenCredits(estimated)
                        .build();
                record.setOwner(task.getOwner());
                usageRecords.add(record);
            }

            // 4/5/6 在同一事务中执行，保证 usage records + 积分冻结 + 任务状态的原子性
            final int credits = totalEstimatedCredits;
            final List<AiTokenUsageRecord> records = usageRecords;
            transactionTemplate.executeWithoutResult(txStatus -> {
                usageRecordRepository.saveAll(records);
                if (credits > 0) {
                    aiCreditsService.tryFreeze(task.getOwner().getId(), credits);
                    task.setEstimatedCredits(credits);
                }
                task.setState(TaskState.PROCESSING);
                asyncTaskRepository.save(task);
            });
            log.debug("[AiAccountTranslateTask] task transaction committed: taskId={}, usageRecords={}, frozenCredits={}",
                    task.getId(), records.size(), credits);

            // 7. 事务提交成功后再将子任务投递到执行队列，避免 usage 记录未落库就被 poll
            for (AiAccountTranslateSubTask subTask : subTasks) {
                subTaskQueuesByAccount
                        .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                        .offer(subTask);
            }
            log.debug("[AiAccountTranslateTask] subtasks enqueued: taskId={}, aiAccountId={}, count={}",
                    task.getId(), aiAccountId, subTasks.size());

            status.setProcessing("已拆分AI账号翻译子任务: " + subTasks.size());
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 拆分任务失败: taskId={}", task.getId(), e);
            AiAccountTranslateTaskStatus existing = runningTasks.get(task.getId());
            if (existing != null && existing.getTotalSubTaskCount() > 0) {
                // 事务已提交，积分已冻结，status 已含有效子任务——不覆盖，由 syncTaskStatus 正常流转
                log.warn("[AiAccountTranslateTask] 事务已提交但后续操作异常，保留已有状态: taskId={}", task.getId());
            } else {
                AiAccountTranslateTaskStatus failStatus = new AiAccountTranslateTaskStatus(
                        task.getId(), 0, null, null, null, task.getOwner());
                failStatus.fail("拆分任务失败: " + e.getMessage());
                runningTasks.put(task.getId(), failStatus);
            }
        }
    }

    private TranslationContentType mapContentType(AiAccountTranslateSubTaskType type) {
        return switch (type) {
            case TEXT -> TranslationContentType.TEXT;
            case HTML -> TranslationContentType.HTML;
            case IMAGE -> TranslationContentType.IMAGE;
        };
    }

    private int estimateSubTaskCredits(TranslateProvider provider, AiAccount account, AiAccountTranslateSubTask subTask) {
        try {
            if (provider != null) {
                return provider.estimateSubTaskCredits(account, subTask);
            }
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] 估算子任务积分失败: subTaskId={}", subTask.getSubTaskId(), e);
        }
        return 1;
    }

    /** 从产品中提取所有需要翻译的内容（标题/摘要/规格文本 + 详情 HTML + 图片 ID），构建子任务列表 */
    private List<AiAccountTranslateSubTask> buildSubTasks(Long taskId, TranslateByAIRequest request) {
        Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
        List<AiAccountTranslateSubTask> subTasks = new ArrayList<>();

        collectTextsToTranslate(product).forEach((hash, text) ->
                subTasks.add(AiAccountTranslateSubTask.text(taskId, hash, text, request)));

        if (StrUtil.isNotBlank(product.getIntroduction())) {
            String hash = DigestUtil.sha256Hex(product.getIntroduction());
            subTasks.add(AiAccountTranslateSubTask.html(taskId, hash, product.getIntroduction(), request));
        }

        for (String imageId : collectImageIds(product)) {
            subTasks.add(AiAccountTranslateSubTask.image(taskId, imageId, request));
        }

        return subTasks;
    }

    private Map<String, String> collectTextsToTranslate(Product product) {
        Map<String, String> textMap = new LinkedHashMap<>();
        addTextIfPresent(textMap, product.getTitle());
        addTextIfPresent(textMap, product.getSummary());
        addTextIfPresent(textMap, product.getWaybillProductName());
        if (product.getSpecificationList() != null) {
            for (ProductSpecification spec : product.getSpecificationList()) {
                if (spec.getAttributes() == null) continue;
                for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                    addTextIfPresent(textMap, attr.getName());
                    addTextIfPresent(textMap, attr.getValue());
                }
            }
        }
        return textMap;
    }

    private void addTextIfPresent(Map<String, String> textMap, String text) {
        if (text != null && !text.isBlank()) {
            textMap.putIfAbsent(DigestUtil.sha256Hex(text), text);
        }
    }

    private List<String> collectImageIds(Product product) {
        Set<String> imageIds = new LinkedHashSet<>();
        if (product.getImageFiles() != null) {
            for (MultimediaFile image : product.getImageFiles()) {
                addImageIdIfPresent(imageIds, image);
            }
        }
        if (product.getSpecificationList() != null) {
            for (ProductSpecification spec : product.getSpecificationList()) {
                addImageIdIfPresent(imageIds, spec.getSpecificationImage());
                if (spec.getAttributes() == null) continue;
                for (ProductSpecificationAttributes attr : spec.getAttributes()) {
                    addImageIdIfPresent(imageIds, attr.getMultimediaFile());
                }
            }
        }
        if (product.getIntroduction() != null) {
            Matcher matcher = IMG_ID_PATTERN.matcher(product.getIntroduction());
            while (matcher.find()) {
                imageIds.add(matcher.group(1));
            }
        }
        return new ArrayList<>(imageIds);
    }

    private void addImageIdIfPresent(Set<String> imageIds, MultimediaFile image) {
        if (image == null || image.getId() == null) return;
        String suffix = image.getSuffix();
        if ("gif".equalsIgnoreCase(suffix)) return;
        if ("webp".equalsIgnoreCase(suffix) && isAnimatedWebp(image)) return;
        imageIds.add(String.valueOf(image.getId()));
    }

    private boolean isAnimatedWebp(MultimediaFile image) {
        try {
            byte[] data = readImageBytes(image);
            return isAnimatedWebp(data);
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] check animated webp failed: imageId={}", image.getId(), e);
            return false;
        }
    }

    private boolean isAnimatedWebp(byte[] data) {
        if (data.length < 20) return false;
        if (data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') return false;
        if (data[8] != 'W' || data[9] != 'E' || data[10] != 'B' || data[11] != 'P') return false;
        if (data.length > 20 && data[12] == 'V' && data[13] == 'P' && data[14] == '8' && data[15] == 'X') {
            return (data[20] & 0x02) != 0;
        }
        for (int i = 12; i < data.length - 4; i++) {
            if (data[i] == 'A' && data[i + 1] == 'N' && data[i + 2] == 'I' && data[i + 3] == 'M') return true;
        }
        return false;
    }

    // --- Cache check ---

    /**
     * 分发前检查翻译缓存。命中时直接完成子任务，返回 true 跳过 Provider 执行。
     * TEXT/HTML 查 TextTranslationCache，IMAGE 查 ImageTranslationCache。
     */
    private boolean tryCompleteFromCache(AiAccountTranslateTaskStatus status, AiAccountTranslateSubTask subTask,
                                          AiAccount account) {
        try {
            Language language = status.getLanguage();
            if (language == null) {
                return false;
            }
            if (subTask.getType() == AiAccountTranslateSubTaskType.TEXT) {
                Optional<TextTranslationCache> cached = textTranslationCacheRepository
                        .findByContentHashAndLanguageIdAndContentType(
                                subTask.getContentKey(), language.getId(), TranslationContentType.TEXT);
                if (cached.isPresent() && StrUtil.isNotBlank(cached.get().getTranslatedText())) {
                    status.completeTextSubTask(subTask, cached.get().getTranslatedText());
                    updateCacheHitUsageRecord(subTask, language.getName(), account, null, null);
                    log.debug("[AiAccountTranslateTask] text cache hit: taskId={}, subTaskId={}, languageId={}",
                            subTask.getTaskId(), subTask.getSubTaskId(), language.getId());
                    return true;
                }
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.HTML) {
                Optional<TextTranslationCache> cached = textTranslationCacheRepository
                        .findByContentHashAndLanguageIdAndContentType(
                                subTask.getContentKey(), language.getId(), TranslationContentType.HTML);
                if (cached.isPresent() && StrUtil.isNotBlank(cached.get().getTranslatedText())) {
                    status.completeHtmlSubTask(subTask, cached.get().getTranslatedText());
                    updateCacheHitUsageRecord(subTask, language.getName(), account, null, null);
                    log.debug("[AiAccountTranslateTask] html cache hit: taskId={}, subTaskId={}, languageId={}",
                            subTask.getTaskId(), subTask.getSubTaskId(), language.getId());
                    return true;
                }
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
                MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
                byte[] imageBytes = readImageBytes(sourceFile);
                String imageHash = DigestUtil.sha256Hex(imageBytes);
                subTask.setImageHash(imageHash);
                Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                        .findByImageHashAndLanguageId(imageHash, language.getId());
                if (cached.isPresent()) {
                    MultimediaFile translatedFile = cached.get().isSkipped() ? null : cached.get().getTranslatedFile();
                    if (translatedFile != null) {
                        status.completeImageSubTask(subTask, translatedFile);
                    } else {
                        status.completeSubTask(subTask);
                    }
                    updateCacheHitUsageRecord(subTask, language.getName(), account, sourceFile, translatedFile);
                    log.debug("[AiAccountTranslateTask] image cache hit: taskId={}, subTaskId={}, languageId={}, skipped={}",
                            subTask.getTaskId(), subTask.getSubTaskId(), language.getId(), cached.get().isSkipped());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] cache lookup failed: taskId={}, subTaskId={}",
                     subTask.getTaskId(), subTask.getSubTaskId(), e);
        }
        return false;
    }

    /**
     * 缓存命中时，为对应的 AiTokenUsageRecord 写入 businessCredits。
     * 优先复制上次同 contentHash+targetLanguage 的实际扣费记录（businessCredits > 0）；无有效历史则按预估兜底。
     * 缓存命中只计 business，不计 actual。
     *
     * @param sourceFile     IMAGE 子任务的原图文件（TEXT/HTML 传 null）
     * @param translatedFile IMAGE 子任务命中缓存后的译图文件（TEXT/HTML 或 skipped 传 null）
     */
    private void updateCacheHitUsageRecord(AiAccountTranslateSubTask subTask, String targetLanguage,
                                              AiAccount account,
                                              MultimediaFile sourceFile, MultimediaFile translatedFile) {
        try {
            usageRecordRepository.findByTaskIdAndSubTaskId(subTask.getTaskId(), subTask.getSubTaskId())
                    .ifPresent(record -> {
                        record.setCacheHit(true);
                        String contentHash = record.getContentHash();

                        // 图片路径
                        if (sourceFile != null) {
                            record.setSourceImagePath(sourceFile.getRelativePath());
                        }
                        if (translatedFile != null) {
                            record.setTranslatedImagePath(translatedFile.getRelativePath());
                            record.setHasImageOutput(true);
                        }

                        Optional<AiTokenUsageRecord> historyOpt = usageRecordRepository
                                .findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
                                        contentHash, targetLanguage);

                        if (historyOpt.isPresent() && historyOpt.get().getBusinessCredits() > 0) {
                            AiTokenUsageRecord history = historyOpt.get();
                            record.setBusinessPromptTokens(history.getBusinessPromptTokens());
                            record.setBusinessCompletionTokens(history.getBusinessCompletionTokens());
                            record.setBusinessThinkingTokens(history.getBusinessThinkingTokens());
                            record.setBusinessTotalTokens(history.getBusinessTotalTokens());
                            record.setBusinessCost(history.getBusinessCost());
                            record.setBusinessCredits(history.getBusinessCredits());
                        } else {
                            TranslationContentType contentType = mapContentType(subTask.getType());
                            int bizPrompt, bizCompletion;
                            if (contentType == TranslationContentType.IMAGE) {
                                bizPrompt = 718;
                                bizCompletion = TokenCostCalculator.estimateImageTokens();
                            } else {
                                int estTokens = TokenCostCalculator.estimateTextTokens(subTask.getContent());
                                bizPrompt = estTokens;
                                bizCompletion = estTokens;
                            }
                            record.setBusinessPromptTokens(bizPrompt);
                            record.setBusinessCompletionTokens(bizCompletion);
                            record.setBusinessThinkingTokens(0);
                            record.setBusinessTotalTokens(bizPrompt + bizCompletion);
                            BigDecimal businessCost = TokenCostCalculator.calculateCost(
                                    contentType, account, bizPrompt, bizCompletion, 0);
                            record.setBusinessCost(businessCost);
                            record.setBusinessCredits(Math.max(TokenCostCalculator.usdToCredits(businessCost), 1));
                        }

                        usageRecordRepository.save(record);
                    });
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] updateCacheHitUsageRecord failed: subTaskId={}",
                     subTask.getSubTaskId(), e);
        }
    }

    // --- Status sync & settlement ---

    /**
     * 将单个任务的内存状态同步到 DB，并在终态时结算积分。
     * - 已取消：通知各 Provider 清理 in-flight → 结算并移除
     * - 子任务全部完成且有成功结果：组装翻译产物 → 标记 COMPLETED（部分失败也保存已有结果）
     * - 所有子任务均失败：标记 FAILED
     * - 终态时：调 settleTask 结算积分（解冻 + 扣实际）
     */
    private void syncSingleTaskStatus(AiAccountTranslateTaskStatus status) {
        asyncTaskRepository.findById(status.getTaskId()).ifPresent(task -> {
            Company company = companyService.companyCached(task.getCompanyId());
            TenantContext.setCurrentTenant(task.getCompanyId(), company);
            try {
                syncSingleTaskStatusInner(status, task);
            } finally {
                TenantContext.clear();
            }
        });
    }

    private void syncSingleTaskStatusInner(AiAccountTranslateTaskStatus status, AsyncTask task) {
        // 外部取消：通知各 Provider 清理 in-flight 子任务并估算费用，然后结算
        if (task.getState() == TaskState.CANCELLED) {
            for (TranslateProvider provider : providerRegistry.values()) {
                try {
                    provider.onTaskCancelling(status.getTaskId());
                } catch (Exception e) {
                    log.warn("[AiAccountTranslateTask] onTaskCancelling failed: provider={}, taskId={}",
                             provider.getProviderType(), status.getTaskId(), e);
                }
            }
            settleTask(task);
            runningTasks.remove(status.getTaskId());
            return;
        }

        // 所有子任务结束且有成功的 → 组装翻译产物（部分失败也组装）
        if (status.isReadyToFinalize()) {
            log.debug("[AiAccountTranslateTask] task ready to finalize: taskId={}, completed={}, failed={}, total={}",
                    status.getTaskId(), status.getCompletedSubTaskCount().get(),
                    status.getFailedSubTaskCount().get(), status.getTotalSubTaskCount());
            finalizeAiAccountTranslateStatus(status);
        }

        // 同步内存状态到 DB
        task.setState(status.getState());
        task.setProgress(status.getProgress());
        task.setMessage(status.getMessage());
        asyncTaskRepository.save(task);

        // 终态（COMPLETED/FAILED）→ 结算积分并从内存移除
        if (status.isFinished()) {
            settleTask(task);
            runningTasks.remove(status.getTaskId());
            log.debug("[AiAccountTranslateTask] finished task removed from memory: taskId={}, state={}, progress={}",
                    status.getTaskId(), status.getState(), status.getProgress());
        }
    }

    /**
     * 积分结算：解冻预估额 + 扣减实际消耗 + 写入结算汇总信息。
     * frozenCredits 来自 AsyncTask.estimatedCredits（loadTask 时写入），
     * actualCredits 来自 SUM(AiTokenUsageRecord.businessCredits)（Provider 回调时累计写入）。
     * <p>
     * 幂等：取消路径下 AsyncTaskService.finalizeBilling 可能已先结算，本方法须跳过避免双重扣费。
     */
    private void settleTask(AsyncTask task) {
        try {
            if (Boolean.TRUE.equals(task.getBillingSettled())) {
                log.debug("[AiAccountTranslateTask] settleTask skipped, already settled: taskId={}", task.getId());
                return;
            }
            Integer frozenCredits = task.getEstimatedCredits();
            if (frozenCredits == null || frozenCredits <= 0) {
                return;
            }
            int actualCredits = usageRecordRepository.sumUnsettledBusinessCreditsByTaskId(task.getId());
            long recordCount = usageRecordRepository.countByTaskId(task.getId());
            int totalPromptTokens = usageRecordRepository.sumBusinessPromptTokensByTaskId(task.getId());
            int totalCompletionTokens = usageRecordRepository.sumBusinessCompletionTokensByTaskId(task.getId());
            int totalThinkingTokens = usageRecordRepository.sumBusinessThinkingTokensByTaskId(task.getId());

            transactionTemplate.executeWithoutResult(txStatus -> {
                aiCreditsService.settle(task.getOwner().getId(), task.getEstimatedCredits(), actualCredits);
                usageRecordRepository.markSettledByTaskId(task.getId());
                task.setEstimatedCredits(0);
                task.setBillingRecordCount(recordCount);
                task.setBillingActualCredits(actualCredits);
                task.setBillingTotalPromptTokens(totalPromptTokens);
                task.setBillingTotalCompletionTokens(totalCompletionTokens);
                task.setBillingTotalThinkingTokens(totalThinkingTokens);
                task.setBillingSettled(true);
                task.setBillingSettledAt(LocalDateTime.now());
                asyncTaskRepository.save(task);
            });
            log.info("[AiAccountTranslateTask] settled taskId={}, frozen={}, actual={}, records={}, promptTokens={}, completionTokens={}, thinkingTokens={}",
                     task.getId(), frozenCredits, actualCredits, recordCount, totalPromptTokens, totalCompletionTokens, totalThinkingTokens);
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] settle failed: taskId={}", task.getId(), e);
        }
    }

    /** 所有子任务结束后，用已完成的翻译组装产品（部分失败也保存已有结果） */
    private void finalizeAiAccountTranslateStatus(AiAccountTranslateTaskStatus status) {
        if (!status.markFinalizing()) {
            return;
        }
        try {
            log.debug("[AiAccountTranslateTask] finalizing translated product: taskId={}, productId={}, languageId={}, countryId={}",
                    status.getTaskId(), status.getProductId(),
                    status.getLanguage() == null ? null : status.getLanguage().getId(), status.getCountryId());
            Product product = productService.getByIdWithSpecifications(status.getProductId());
            Country country = countryService.getById(status.getCountryId());
            productService.assembleTranslatedProduct(
                    product, status.getLanguage(), country, status.getOwner(),
                    status.getTranslatedTextMap(), status.getTranslatedHtml(), status.getTranslatedImageMap());
            if (status.getFailedSubTaskCount().get() > 0) {
                status.complete("翻译完成(部分失败: " + status.getFailedSubTaskCount().get() + " 个子任务)");
            } else {
                status.complete();
            }
            log.debug("[AiAccountTranslateTask] translated product finalized: taskId={}, completed={}, failed={}",
                    status.getTaskId(), status.getCompletedSubTaskCount().get(), status.getFailedSubTaskCount().get());
        } catch (Exception e) {
            status.fail("assemble translated product failed: " + e.getMessage());
            log.error("[AiAccountTranslateTask] assemble translated product failed: taskId={}",
                      status.getTaskId(), e);
        } finally {
            status.markFinalized();
        }
    }

    // --- Utility ---

    private byte[] readImageBytes(MultimediaFile file) throws Exception {
        try (InputStream inputStream = multimediaFileService.download(String.valueOf(file.getId()), 0);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }
}
