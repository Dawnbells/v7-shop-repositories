package cn.v7soft.admin.task;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.impl.AiCreditsService;
import cn.v7soft.admin.task.provider.TranslateProvider;
import cn.v7soft.admin.task.provider.TranslateProviderCallback;
import cn.v7soft.admin.task.provider.TranslateTaskCallbackAdapter;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 账号翻译任务编排器。
 * <p>
 * 通过三个定时器驱动任务生命周期：
 * <ol>
 *   <li>任务拆接定时器 (executePendingTasks, 60s) — 拉取 PENDING 的 AsyncTask，拆分为子任务入队</li>
 *   <li>子任务执行定时器 (executeSubTasks, 1s) — 遍历所有账号队列（优先失败队列），
 *       通过 providerRegistry 获取对应 Provider 并调用 executeSubTask 分发</li>
 *   <li>任务状态更新定时器 (syncTaskStatus, 5s) — 触发 Provider 过期回收，同步内存状态到 DB</li>
 * </ol>
 * <p>
 * 状态流转：待执行队列(FIFO) / 失败队列(FIFO) → 执行中 → 成功 / 失败队列 / 待执行队尾
 * <p>
 * 实现 TranslateTaskContext 接口，供 TranslateTaskCallbackAdapter 回调时访问内部状态。
 */
@Slf4j
@Component
public class AiAccountTranslateTask implements TranslateTaskContext {

    private static final int MAX_TASKS_PER_ROUND = 5;
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private final AsyncTaskRepository asyncTaskRepository;
    private final IProductService productService;
    private final IAiAccountService aiAccountService;
    private final IAsyncTaskService asyncTaskService;
    private final IMultimediaFileService multimediaFileService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final TextTranslationCacheRepository textTranslationCacheRepository;
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;
    private final AiCreditsService aiCreditsService;
    private final List<TranslateProvider> providers;

    // 按账号隔离的待执行子任务队列（FIFO，先提交先执行）
    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> subTaskQueuesByAccount = new ConcurrentHashMap<>();
    // 按账号隔离的失败子任务队列（FIFO，优先于普通队列被分发）
    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> failedSubTaskQueuesByAccount = new ConcurrentHashMap<>();
    // 运行中的 AsyncTask 状态（内存态），由 syncTaskStatus 定时器周期性写回 DB
    private final ConcurrentMap<Long, AiAccountTranslateTaskStatus> runningTasks = new ConcurrentHashMap<>();
    // 账号级并发/限流运行时状态
    private final ConcurrentMap<Long, AiAccountRuntimeState> accountRuntimeStates = new ConcurrentHashMap<>();
    private final AtomicBoolean loadingTasks = new AtomicBoolean(false);
    private final AtomicBoolean executingSubTasks = new AtomicBoolean(false);
    private final AtomicBoolean syncingTaskStatus = new AtomicBoolean(false);

    // AiProvider 枚举 -> Provider 实现的映射表，@PostConstruct 时构建
    private Map<AiProvider, TranslateProvider> providerRegistry;

    public AiAccountTranslateTask(AsyncTaskRepository asyncTaskRepository,
                                  IProductService productService,
                                  IAiAccountService aiAccountService,
                                  IAsyncTaskService asyncTaskService,
                                  IMultimediaFileService multimediaFileService,
                                  ILanguageService languageService,
                                  ICountryService countryService,
                                  ImageTranslationCacheRepository imageTranslationCacheRepository,
                                  TextTranslationCacheRepository textTranslationCacheRepository,
                                  AiTokenUsageRecordRepository aiTokenUsageRecordRepository,
                                  AiCreditsService aiCreditsService,
                                  List<TranslateProvider> providers) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.productService = productService;
        this.aiAccountService = aiAccountService;
        this.asyncTaskService = asyncTaskService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.imageTranslationCacheRepository = imageTranslationCacheRepository;
        this.textTranslationCacheRepository = textTranslationCacheRepository;
        this.aiTokenUsageRecordRepository = aiTokenUsageRecordRepository;
        this.aiCreditsService = aiCreditsService;
        this.providers = providers;
    }

    @PostConstruct
    public void initialize() {
        TranslateProviderCallback callback = new TranslateTaskCallbackAdapter(this);
        providerRegistry = new ConcurrentHashMap<>();
        for (TranslateProvider provider : providers) {
            provider.setCallback(callback);
            providerRegistry.put(provider.getProviderType(), provider);
        }

        resetProcessingTasksOnStartup();
    }

    private void resetProcessingTasksOnStartup() {
        List<AsyncTask> processingTasks = asyncTaskRepository.findByTaskTypeAndState(
                TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE,
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
    }

    @Override
    public void pushToPendingQueue(AiAccountTranslateSubTask subTask) {
        subTaskQueuesByAccount
                .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                .offer(subTask);
    }

    // --- Scheduled timers ---

    @Scheduled(fixedDelay = 60 * 1000, initialDelay = 30 * 1000)
    public void executePendingTasks() {
        if (!loadingTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            List<AsyncTask> tasks = asyncTaskRepository.findByTaskTypeAndStateOrderByCreateTimeAsc(
                    TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE,
                    TaskState.PENDING,
                    PageRequest.of(0, MAX_TASKS_PER_ROUND));

            for (AsyncTask task : tasks) {
                if (runningTasks.containsKey(task.getId())) {
                    continue;
                }
                if (!aiCreditsService.hasAvailableCredits(task.getOwner().getId())) {
                    task.setState(TaskState.INSUFFICIENT_CREDITS);
                    task.setMessage("AI积分不足，请充值后重试");
                    asyncTaskRepository.save(task);
                    log.info("[AiAccountTranslateTask] 积分不足，标记 INSUFFICIENT_CREDITS: taskId={}, userId={}",
                            task.getId(), task.getOwner().getId());
                    continue;
                }
                loadTask(task);
            }
        } finally {
            loadingTasks.set(false);
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 35 * 1000)
    public void executeSubTasks() {
        if (!executingSubTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            Set<Long> allAccountIds = new LinkedHashSet<>();
            allAccountIds.addAll(failedSubTaskQueuesByAccount.keySet());
            allAccountIds.addAll(subTaskQueuesByAccount.keySet());

            for (Long aiAccountId : allAccountIds) {
                executeAccountSubTasks(aiAccountId);
            }
        } finally {
            executingSubTasks.set(false);
        }
    }

    @Scheduled(fixedDelay = 5 * 1000, initialDelay = 40 * 1000)
    public void syncTaskStatus() {
        if (!syncingTaskStatus.compareAndSet(false, true)) {
            return;
        }
        try {
            for (TranslateProvider provider : providerRegistry.values()) {
                provider.reclaimExpiredAssignments();
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
     * 对单个账号执行子任务分发：
     * 1. 合并失败队列和普通队列的任务数
     * 2. 根据账号流控 (CONCURRENCY/RPD_RPM) 预留可执行槽位
     * 3. 优先从失败队列取任务，其次从普通队列
     * 4. 通过 providerRegistry 获取对应 Provider 并调用 executeSubTask
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

        Queue<AiAccountTranslateSubTask> failedQueue = failedSubTaskQueuesByAccount.get(aiAccountId);
        Queue<AiAccountTranslateSubTask> pendingQueue = subTaskQueuesByAccount.get(aiAccountId);
        int totalPending = queueSize(failedQueue) + queueSize(pendingQueue);

        int executableCount = runtimeState.reserveSlots(account, totalPending);
        int unusedReservations = 0;
        for (int i = 0; i < executableCount; i++) {
            AiAccountTranslateSubTask subTask = pollFromQueues(failedQueue, pendingQueue);
            if (subTask == null) {
                unusedReservations++;
                continue;
            }
            provider.executeSubTask(subTask);
        }
        if (unusedReservations > 0) {
            runtimeState.releaseUnusedReservations(unusedReservations);
        }
        cleanupAccountState(aiAccountId, runtimeState);
    }

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

    private void failQueuedSubTasks(Queue<AiAccountTranslateSubTask> queue, String message) {
        AiAccountTranslateSubTask subTask;
        while ((subTask = queue.poll()) != null) {
            AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
            if (taskStatus == null) {
                continue;
            }
            subTask.fail(message);
            taskStatus.failPendingSubTask(subTask, message);
        }
    }

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

    private void loadTask(AsyncTask task) {
        try {
            TranslateByAIRequest request = JSONUtil.toBean(task.getParameters(), TranslateByAIRequest.class);
            List<AiAccountTranslateSubTask> subTasks = buildSubTasks(task.getId(), request);
            Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
            Language language = languageService.getById(Long.parseLong(request.getLanguageId()));
            Country country = countryService.getById(Long.parseLong(request.getCountryId()));
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(
                    task.getId(), subTasks.size(), product, language, country, task.getOwner());
            AiAccountTranslateTaskStatus existing = runningTasks.putIfAbsent(task.getId(), status);
            if (existing != null) {
                return;
            }

            if (subTasks.isEmpty()) {
                status.complete();
                return;
            }

            int totalEstimatedCredits = 0;
            for (AiAccountTranslateSubTask subTask : subTasks) {
                subTask.setOwner(task.getOwner());
                status.addSubTask(subTask);
                if (tryCompleteFromCache(status, subTask)) {
                    continue;
                }
                if (subTask.getType() != AiAccountTranslateSubTaskType.IMAGE) {
                    completeLocalNoopSubTask(subTask);
                    status.completeSubTask(subTask);
                    continue;
                }
                totalEstimatedCredits += estimateSubTaskCredits(subTask);
                subTaskQueuesByAccount
                        .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                        .offer(subTask);
            }

            if (totalEstimatedCredits > 0) {
                aiCreditsService.tryFreeze(task.getOwner().getId(), totalEstimatedCredits);
                task.setEstimatedCredits(totalEstimatedCredits);
                asyncTaskRepository.save(task);
            }

            status.setProcessing("已拆分AI账号翻译子任务: " + subTasks.size());
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 拆分任务失败: taskId={}", task.getId(), e);
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(task.getId(), 0, null, null, null, task.getOwner());
            status.fail("拆分任务失败: " + e.getMessage());
            runningTasks.put(task.getId(), status);
        }
    }

    private int estimateSubTaskCredits(AiAccountTranslateSubTask subTask) {
        try {
            AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
            TranslateProvider provider = providerRegistry.get(account.getProvider());
            if (provider != null) {
                return provider.estimateSubTaskCredits(subTask);
            }
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] 估算子任务积分失败: subTaskId={}", subTask.getSubTaskId(), e);
        }
        return 1;
    }

    private List<AiAccountTranslateSubTask> buildSubTasks(Long taskId, TranslateByAIRequest request) {
        Product product = productService.getByIdWithSpecifications(Long.parseLong(request.getProductId()));
        List<AiAccountTranslateSubTask> subTasks = new ArrayList<>();

        collectTextsToTranslate(product).forEach((hash, text) ->
                subTasks.add(AiAccountTranslateSubTask.text(taskId, hash, text, request)));

        if (product.getIntroduction() != null && !product.getIntroduction().isBlank()) {
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
                if (spec.getAttributes() == null) {
                    continue;
                }
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
                if (spec.getAttributes() == null) {
                    continue;
                }
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
        if (image != null && image.getId() != null && !"gif".equalsIgnoreCase(image.getSuffix())) {
            imageIds.add(String.valueOf(image.getId()));
        }
    }

    // --- Cache & status sync ---

    private boolean tryCompleteFromCache(AiAccountTranslateTaskStatus status, AiAccountTranslateSubTask subTask) {
        try {
            Language language = resolveLanguage(subTask);
            if (subTask.getType() == AiAccountTranslateSubTaskType.TEXT) {
                Optional<TextTranslationCache> cached = textTranslationCacheRepository
                        .findByContentHashAndLanguageIdAndContentType(
                                subTask.getContentKey(), language.getId(), TranslationContentType.TEXT);
                if (cached.isPresent() && StrUtil.isNotBlank(cached.get().getTranslatedText())) {
                    status.completeTextSubTask(subTask, cached.get().getTranslatedText());
                    saveCacheHitTokenRecord(subTask, TranslationContentType.TEXT,
                            subTask.getContent(), cached.get().getTranslatedText(), null, null);
                    return true;
                }
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.HTML) {
                Optional<TextTranslationCache> cached = textTranslationCacheRepository
                        .findByContentHashAndLanguageIdAndContentType(
                                subTask.getContentKey(), language.getId(), TranslationContentType.HTML);
                if (cached.isPresent() && StrUtil.isNotBlank(cached.get().getTranslatedText())) {
                    status.completeHtmlSubTask(subTask, cached.get().getTranslatedText());
                    saveCacheHitTokenRecord(subTask, TranslationContentType.HTML,
                            subTask.getContent(), cached.get().getTranslatedText(), null, null);
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
                    applyCachedImageSubTask(status, subTask, cached.get(), sourceFile);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] cache lookup failed: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), e);
        }
        return false;
    }

    private void applyCachedImageSubTask(AiAccountTranslateTaskStatus status,
                                         AiAccountTranslateSubTask subTask,
                                         ImageTranslationCache cached,
                                         MultimediaFile sourceFile) {
        MultimediaFile translatedFile = cached.isSkipped() ? null : cached.getTranslatedFile();
        if (translatedFile != null) {
            status.completeImageSubTask(subTask, translatedFile);
        } else {
            status.completeSubTask(subTask);
        }
        saveCacheHitTokenRecord(subTask, TranslationContentType.IMAGE, null, null,
                sourceFile, translatedFile);
    }

    private void completeLocalNoopSubTask(AiAccountTranslateSubTask subTask) {
        subTask.complete();
    }

    private void syncSingleTaskStatus(AiAccountTranslateTaskStatus status) {
        asyncTaskRepository.findById(status.getTaskId()).ifPresent(task -> {
            if (task.getState() == TaskState.CANCELLED) {
                runningTasks.remove(status.getTaskId());
                asyncTaskService.finalizeBilling(task.getId());
                return;
            }

            if (status.isReadyToFinalize()) {
                finalizeAiAccountTranslateStatus(status);
            }

            task.setState(status.getState());
            task.setProgress(status.getProgress());
            task.setMessage(status.getMessage());
            asyncTaskRepository.save(task);

            if (status.isFinished()) {
                runningTasks.remove(status.getTaskId());
                asyncTaskService.finalizeBilling(task.getId());
            }
        });
    }

    private void finalizeAiAccountTranslateStatus(AiAccountTranslateTaskStatus status) {
        if (!status.markFinalizing()) {
            return;
        }
        try {
            if (status.getFailedSubTaskCount().get() > 0) {
                status.fail("AI account translate failed: " + status.getFailedSubTaskCount().get());
                return;
            }
            productService.assembleTranslatedProduct(
                    status.getProduct(), status.getLanguage(), status.getCountry(), status.getOwner(),
                    status.getTranslatedTextMap(), status.getTranslatedHtml(), status.getTranslatedImageMap());
            status.complete();
        } catch (Exception e) {
            status.fail("assemble translated product failed: " + e.getMessage());
            log.error("[AiAccountTranslateTask] assemble translated product failed: taskId={}",
                    status.getTaskId(), e);
        } finally {
            status.markFinalized();
        }
    }

    // --- Utility methods ---

    private Language resolveLanguage(AiAccountTranslateSubTask subTask) {
        return languageService.getById(Long.parseLong(subTask.getLanguageId()));
    }

    private byte[] readImageBytes(MultimediaFile file) throws Exception {
        try (InputStream inputStream = multimediaFileService.download(String.valueOf(file.getId()), 0);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void saveCacheHitTokenRecord(AiAccountTranslateSubTask subTask, TranslationContentType contentType,
                                         String sourceText, String translatedText,
                                         MultimediaFile sourceFile, MultimediaFile translatedFile) {
        int promptTokens;
        int completionTokens;
        if (contentType == TranslationContentType.IMAGE) {
            promptTokens = 718;
            completionTokens = TokenCostCalculator.estimateImageTokens();
        } else {
            promptTokens = TokenCostCalculator.estimateTextTokens(sourceText);
            completionTokens = promptTokens;
        }
        saveTokenUsageRecord(subTask, contentType, true, sourceText, translatedText,
                sourceFile != null ? sourceFile.getRelativePath() : null,
                translatedFile != null ? translatedFile.getRelativePath() : null,
                promptTokens, completionTokens, 0, null, translatedFile != null);
    }

    private void saveTokenUsageRecord(AiAccountTranslateSubTask subTask,
                                      TranslationContentType contentType,
                                      boolean cacheHit,
                                      String sourceText,
                                      String translatedText,
                                      String sourceImagePath,
                                      String translatedImagePath,
                                      int businessPromptTokens,
                                      int businessCompletionTokens,
                                      int businessThinkingTokens,
                                      Long elapsedMs,
                                      boolean hasImageOutput) {
        try {
            if (aiTokenUsageRecordRepository.existsByTaskIdAndContentHashAndTargetLanguage(
                    subTask.getTaskId(), subTask.getContentKey(), resolveLanguage(subTask).getName())) {
                return;
            }
            BigDecimal businessCost = TokenCostCalculator.calculateCost(
                    contentType, InvokeMode.STANDARD,
                    businessPromptTokens, businessCompletionTokens, businessThinkingTokens);
            AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
            AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                    .taskId(subTask.getTaskId())
                    .aiAccount(account)
                    .contentType(contentType)
                    .contentHash(subTask.getContentKey())
                    .targetLanguage(resolveLanguage(subTask).getName())
                    .cacheHit(cacheHit)
                    .model(StrUtil.blankToDefault(account.getModel(), "turboflow"))
                    .invokeMode(InvokeMode.STANDARD)
                    .actualPromptTokens(0)
                    .actualCompletionTokens(0)
                    .actualThinkingTokens(0)
                    .actualTotalTokens(0)
                    .businessPromptTokens(businessPromptTokens)
                    .businessCompletionTokens(businessCompletionTokens)
                    .businessThinkingTokens(businessThinkingTokens)
                    .businessTotalTokens(businessPromptTokens + businessCompletionTokens + businessThinkingTokens)
                    .actualCost(BigDecimal.ZERO)
                    .businessCost(businessCost)
                    .businessCredits(TokenCostCalculator.usdToCredits(businessCost))
                    .elapsedMs(elapsedMs)
                    .hasImageOutput(hasImageOutput)
                    .sourceText(sourceText)
                    .translatedText(translatedText)
                    .sourceImagePath(sourceImagePath)
                    .translatedImagePath(translatedImagePath)
                    .build();
            record.setOwner(subTask.getOwner());
            aiTokenUsageRecordRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            log.debug("[AiAccountTranslateTask] token usage already exists: taskId={}, hash={}",
                    subTask.getTaskId(), subTask.getContentKey());
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] save token usage failed: taskId={}, hash={}",
                    subTask.getTaskId(), subTask.getContentKey(), e);
        }
    }
}
