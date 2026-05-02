package cn.v7soft.admin.task;

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

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.service.impl.AiCreditsService;
import cn.v7soft.admin.task.provider.SubTaskResult;
import cn.v7soft.admin.task.provider.TranslateProvider;
import cn.v7soft.admin.task.provider.TranslateProviderCallback;
import cn.v7soft.admin.task.provider.TranslateTaskCallbackAdapter;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTranslateUsageRecord;
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
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTranslateUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AiAccountTranslateTask implements TranslateTaskContext {

    private static final int MAX_TASKS_PER_ROUND = 1;
    private static final Pattern IMG_ID_PATTERN = Pattern.compile("/multimedia/([0-9]+)");

    private final AsyncTaskRepository asyncTaskRepository;
    private final IProductService productService;
    private final IAiAccountService aiAccountService;
    private final IMultimediaFileService multimediaFileService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final TextTranslationCacheRepository textTranslationCacheRepository;
    private final AiTranslateUsageRecordRepository usageRecordRepository;
    private final AiCreditsService aiCreditsService;
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
                                  IMultimediaFileService multimediaFileService,
                                  ILanguageService languageService,
                                  ICountryService countryService,
                                  ImageTranslationCacheRepository imageTranslationCacheRepository,
                                  TextTranslationCacheRepository textTranslationCacheRepository,
                                  AiTranslateUsageRecordRepository usageRecordRepository,
                                  AiCreditsService aiCreditsService,
                                  List<TranslateProvider> providers) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.productService = productService;
        this.aiAccountService = aiAccountService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.imageTranslationCacheRepository = imageTranslationCacheRepository;
        this.textTranslationCacheRepository = textTranslationCacheRepository;
        this.usageRecordRepository = usageRecordRepository;
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
                        if (result.getTranslatedFile() != null) {
                            record.setTranslatedImagePath(result.getTranslatedFile().getRelativePath());
                            record.setHasImageOutput(true);
                        }
                        if (result.getTranslatedText() != null) {
                            record.setTranslatedText(result.getTranslatedText());
                        }
                        if (result.getTranslatedHtml() != null) {
                            record.setTranslatedText(result.getTranslatedHtml());
                        }
                        record.setAttemptCount(subTask.getAttemptCount().get());
                        usageRecordRepository.save(record);
                    });
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] updateUsageRecord failed: subTaskId={}", subTask.getSubTaskId(), e);
        }
    }

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
                    });
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] accumulateUsageRecord failed: subTaskId={}", subTask.getSubTaskId(), e);
        }
    }

    private int safeAdd(Integer a, int b) {
        return (a == null ? 0 : a) + b;
    }

    // --- Scheduled timers ---

    @Scheduled(fixedDelay = 5 * 1000, initialDelay = 60 * 1000)
    public void executePendingTasks() {
        if (!loadingTasks.compareAndSet(false, true)) {
            return;
        }
        try {
            Optional<AsyncTask> taskOptional = asyncTaskRepository.findByTaskTypeAndStateOrderByCreateTimeAsc(
                    TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE,
                    TaskState.PENDING,
                    PageRequest.of(0, MAX_TASKS_PER_ROUND));

            if (taskOptional.isEmpty()) {
                return;
            }
            AsyncTask task = taskOptional.get();
            if (runningTasks.containsKey(task.getId())) {
                task.setState(TaskState.PROCESSING);
                asyncTaskRepository.save(task);
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

    private void executeAccountSubTasks(Long aiAccountId) {
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(aiAccountId, AiAccountRuntimeState::new);
        AiAccount account;
        try {
            account = aiAccountService.getById(aiAccountId);
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 获取AI账号失败: aiAccountId={}", aiAccountId, e);
            Queue<AiAccountTranslateSubTask> failedQueue = failedSubTaskQueuesByAccount.get(aiAccountId);
            Queue<AiAccountTranslateSubTask> pendingQueue = subTaskQueuesByAccount.get(aiAccountId);
            if (failedQueue != null) failQueuedSubTasks(failedQueue, "AI账号不存在或不可用: " + aiAccountId);
            if (pendingQueue != null) failQueuedSubTasks(pendingQueue, "AI账号不存在或不可用: " + aiAccountId);
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
        if (subTask != null) return subTask;
        return pendingQueue == null ? null : pendingQueue.poll();
    }

    private int queueSize(Queue<?> queue) {
        return queue == null ? 0 : queue.size();
    }

    private void failQueuedSubTasks(Queue<AiAccountTranslateSubTask> queue, String message) {
        AiAccountTranslateSubTask subTask;
        while ((subTask = queue.poll()) != null) {
            AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
            if (taskStatus == null) continue;
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
            if (pendingQueue != null) subTaskQueuesByAccount.remove(aiAccountId, pendingQueue);
            if (failedQueue != null) failedSubTaskQueuesByAccount.remove(aiAccountId, failedQueue);
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
                    task.getId(), subTasks.size(), product.getId(), language.getId(), country.getId(), task.getOwner());
            AiAccountTranslateTaskStatus existing = runningTasks.putIfAbsent(task.getId(), status);
            if (existing != null) {
                return;
            }

            if (subTasks.isEmpty()) {
                status.complete();
                return;
            }

            int totalEstimatedCredits = 0;
            List<AiTranslateUsageRecord> usageRecords = new ArrayList<>();

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

                int estimated = estimateSubTaskCredits(subTask);
                totalEstimatedCredits += estimated;

                AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
                AiTranslateUsageRecord record = AiTranslateUsageRecord.builder()
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

                subTaskQueuesByAccount
                        .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                        .offer(subTask);
            }

            if (!usageRecords.isEmpty()) {
                usageRecordRepository.saveAll(usageRecords);
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

    private TranslationContentType mapContentType(AiAccountTranslateSubTaskType type) {
        return switch (type) {
            case TEXT -> TranslationContentType.TEXT;
            case HTML -> TranslationContentType.HTML;
            case IMAGE -> TranslationContentType.IMAGE;
        };
    }

    private int estimateSubTaskCredits(AiAccountTranslateSubTask subTask) {
        try {
            AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
            TranslateProvider provider = providerRegistry.get(account.getProvider());
            if (provider != null) return provider.estimateSubTaskCredits(subTask);
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
                    return true;
                }
            } else if (subTask.getType() == AiAccountTranslateSubTaskType.HTML) {
                Optional<TextTranslationCache> cached = textTranslationCacheRepository
                        .findByContentHashAndLanguageIdAndContentType(
                                subTask.getContentKey(), language.getId(), TranslationContentType.HTML);
                if (cached.isPresent() && StrUtil.isNotBlank(cached.get().getTranslatedText())) {
                    status.completeHtmlSubTask(subTask, cached.get().getTranslatedText());
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
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("[AiAccountTranslateTask] cache lookup failed: taskId={}, subTaskId={}",
                     subTask.getTaskId(), subTask.getSubTaskId(), e);
        }
        return false;
    }

    private void completeLocalNoopSubTask(AiAccountTranslateSubTask subTask) {
        subTask.complete();
    }

    private void syncSingleTaskStatus(AiAccountTranslateTaskStatus status) {
        asyncTaskRepository.findById(status.getTaskId()).ifPresent(task -> {
            if (task.getState() == TaskState.CANCELLED) {
                settleTask(task);
                runningTasks.remove(status.getTaskId());
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
                settleTask(task);
                runningTasks.remove(status.getTaskId());
            }
        });
    }

    private void settleTask(AsyncTask task) {
        try {
            Integer frozenCredits = task.getEstimatedCredits();
            if (frozenCredits == null || frozenCredits <= 0) {
                return;
            }
            int actualCredits = usageRecordRepository.sumBusinessCreditsByTaskId(task.getId());
            aiCreditsService.settle(task.getOwner().getId(), frozenCredits, actualCredits);
            usageRecordRepository.markSettledByTaskId(task.getId());
            log.info("[AiAccountTranslateTask] settled taskId={}, frozen={}, actual={}",
                     task.getId(), frozenCredits, actualCredits);
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] settle failed: taskId={}", task.getId(), e);
        }
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
            Product product = productService.getByIdWithSpecifications(status.getProductId());
            Language language = languageService.getById(status.getLanguageId());
            Country country = countryService.getById(status.getCountryId());
            productService.assembleTranslatedProduct(
                    product, language, country, status.getOwner(),
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

    // --- Utility ---

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
}
