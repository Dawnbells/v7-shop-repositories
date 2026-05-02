package cn.v7soft.admin.task;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeFailRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeHeartbeatRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeHeartbeatResponse;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IProductService;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.Product;
import cn.v7soft.dao.entities.primary.ProductSpecification;
import cn.v7soft.dao.entities.primary.ProductSpecificationAttributes;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TextTranslationCache;
import cn.v7soft.dao.enums.AiRateLimitMode;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.TextTranslationCacheRepository;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAccountTranslateTask {

    private static final int MAX_TASKS_PER_ROUND = 10;
    private static final int DEFAULT_MAX_CONCURRENCY = 1;
    private static final int TURBOFLOW_LEASE_MINUTES = 10;
    private static final int TURBOFLOW_MAX_ATTEMPTS = 3;
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
    // 普通待分发栈：新拆分出的图片任务按账号隔离，poll 时按栈顶分配。
    private final ConcurrentMap<Long, ConcurrentLinkedDeque<AiAccountTranslateSubTask>> subTaskStacksByAccount = new ConcurrentHashMap<>();
    // 失败优先栈：插件上报失败或 lease 过期的任务进入这里，下一次 poll 优先重试。
    private final ConcurrentMap<Long, ConcurrentLinkedDeque<AiAccountTranslateSubTask>> failedSubTaskStacksByAccount = new ConcurrentHashMap<>();
    // 运行中异步任务状态只保存在内存，定时同步到 AsyncTask 表。
    private final ConcurrentMap<Long, AiAccountTranslateTaskStatus> runningTasks = new ConcurrentHashMap<>();
    // 账号级并发/限流状态，TurboFlow 也用它保证同一账号不会超过配置并发。
    private final ConcurrentMap<Long, AiAccountRuntimeState> accountRuntimeStates = new ConcurrentHashMap<>();
    // 插件在线状态，仅用于观测；服务重启后可以丢失。
    private final ConcurrentMap<String, TurboFlowBridgeState> turboFlowBridgeStates = new ConcurrentHashMap<>();
    // assignmentId -> 子任务。只有拿到 assignmentId 的插件才能 complete/fail 对应任务。
    private final ConcurrentMap<String, AiAccountTranslateSubTask> turboFlowAssignments = new ConcurrentHashMap<>();
    private final AtomicBoolean loadingTasks = new AtomicBoolean(false);
    private final AtomicBoolean executingSubTasks = new AtomicBoolean(false);
    private final AtomicBoolean syncingTaskStatus = new AtomicBoolean(false);

    @PostConstruct
    public void resetProcessingTasksOnStartup() {
        // assignment 和运行态都在内存中，服务重启后无法继续旧的 PROCESSING 任务。
        // 重置为 PENDING 后会重新拆分任务；已完成内容依靠翻译缓存跳过重复执行。
        List<AsyncTask> processingTasks = asyncTaskRepository.findByTaskTypeAndState(
                TaskType.PRODUCT_AI_ACCOUNT_TRANSLATE,
                TaskState.PROCESSING);
        if (processingTasks.isEmpty()) {
            return;
        }
        for (AsyncTask task : processingTasks) {
            task.setState(TaskState.PENDING);
            task.setProgress(0);
            task.setMessage("TurboFlow task reset after server restart");
        }
        asyncTaskRepository.saveAll(processingTasks);
        log.warn("[AiAccountTranslateTask] reset processing TurboFlow tasks to pending on startup: count={}",
                processingTasks.size());
    }

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
            for (Map.Entry<Long, ConcurrentLinkedDeque<AiAccountTranslateSubTask>> entry : subTaskStacksByAccount.entrySet()) {
                executeAccountSubTasks(entry.getKey(), entry.getValue());
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
            for (AiAccountTranslateTaskStatus status : runningTasks.values()) {
                syncSingleTaskStatus(status);
            }
        } finally {
            syncingTaskStatus.set(false);
        }
    }

    public TurboFlowBridgeHeartbeatResponse turboFlowHeartbeat(String token, TurboFlowBridgeHeartbeatRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        String bridgeId = normalizeBridgeId(request.getBridgeId());
        turboFlowBridgeStates.put(bridgeId, TurboFlowBridgeState.from(account.getId(), request));
        return TurboFlowBridgeHeartbeatResponse.builder()
                .accepted(true)
                .aiAccountId(account.getId())
                .message("ready")
                .build();
    }

    public TurboFlowBridgeTaskResponse pollTurboFlowTask(String token, TurboFlowBridgePollRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        String bridgeId = normalizeBridgeId(request.getBridgeId());
        turboFlowBridgeStates.put(bridgeId, TurboFlowBridgeState.from(account.getId(), request));

        ConcurrentLinkedDeque<AiAccountTranslateSubTask> failedStack = failedSubTaskStacksByAccount.get(account.getId());
        ConcurrentLinkedDeque<AiAccountTranslateSubTask> stack = subTaskStacksByAccount.get(account.getId());
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(account.getId(), AiAccountRuntimeState::new);

        synchronized (runtimeState) {
            // 分发、过期回收、并发占用必须在账号锁内完成，避免多个插件拿到同一个任务。
            reclaimExpiredTurboFlowAssignments(account.getId(), runtimeState);
            failedStack = failedSubTaskStacksByAccount.get(account.getId());
            stack = subTaskStacksByAccount.get(account.getId());
            if (isEmpty(failedStack) && isEmpty(stack)) {
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
            }

            if (runtimeState.reserveSlots(account, 1) <= 0) {
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("concurrency limit").build();
            }

            AiAccountTranslateSubTask subTask = pollTurboFlowSubTask(failedStack, stack);
            if (subTask == null) {
                runtimeState.releaseFinishedSlot();
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
            }
            if (subTask.getType() != AiAccountTranslateSubTaskType.IMAGE) {
                runtimeState.releaseFinishedSlot();
                completeLocalNoopSubTask(subTask);
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("non-image task skipped").build();
            }

            AiAccountTranslateTaskStatus status = runningTasks.get(subTask.getTaskId());
            if (status == null) {
                runtimeState.releaseFinishedSlot();
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("task missing").build();
            }

            try {
                MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
                byte[] imageBytes = readImageBytes(sourceFile);
                String imageHash = DigestUtil.sha256Hex(imageBytes);
                subTask.setImageHash(imageHash);

                Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                        .findByImageHashAndLanguageId(imageHash, Long.parseLong(subTask.getLanguageId()));
                if (cached.isPresent()) {
                    // 二次缓存检查：任务入栈后到插件 poll 之间，其他任务可能已经生成了同图同语言缓存。
                    runtimeState.releaseFinishedSlot();
                    applyCachedImageSubTask(status, subTask, cached.get(), sourceFile);
                    return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("cache hit").build();
                }

                String assignmentId = UUID.randomUUID().toString();
                LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(TURBOFLOW_LEASE_MINUTES);
                // dispatch 后任务进入 PROCESSING，并生成 lease；超时未回调会被放入失败优先栈重试。
                subTask.dispatch(bridgeId, assignmentId, leaseUntil);
                turboFlowAssignments.put(assignmentId, subTask);
                status.dispatchSubTask(subTask);

                return TurboFlowBridgeTaskResponse.builder()
                        .hasTask(true)
                        .taskId(subTask.getTaskId())
                        .subTaskId(subTask.getSubTaskId())
                        .assignmentId(assignmentId)
                        .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                        .fileName(sourceFile.getName() + "." + sourceFile.getSuffix())
                        .mimeType(toMimeType(sourceFile.getSuffix()))
                        .targetLanguage(resolveLanguage(subTask).getName())
                        .targetLanguageCode(resolveLanguage(subTask).getCode())
                        .sourceWidth(sourceFile.getWidth())
                        .sourceHeight(sourceFile.getHeight())
                        .leaseUntil(leaseUntil)
                        .build();
            } catch (Exception e) {
                runtimeState.releaseFinishedSlot();
                pushTurboFlowSubTask(subTask, true);
                log.error("[TurboFlowBridge] 分发任务失败: taskId={}, subTaskId={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), e);
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("dispatch failed: " + e.getMessage()).build();
            }
        }
    }

    public void completeTurboFlowTask(String token, TurboFlowBridgeCompleteRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        AiAccountTranslateSubTask subTask = turboFlowAssignments.get(request.getAssignmentId());
        if (subTask == null) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        // 防止多个账号/多个插件串任务：token 对应账号和 bridgeId 都必须匹配 assignment。
        if (!account.getId().equals(subTask.getAiAccountId())) {
            throw new IllegalArgumentException("assignment does not belong to account");
        }
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            throw new IllegalArgumentException("assignment does not belong to bridge");
        }
        if (!turboFlowAssignments.remove(request.getAssignmentId(), subTask)) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(subTask.getAiAccountId(), AiAccountRuntimeState::new);
        try {
            byte[] imageBytes = decodeBase64Image(request.getResultImageBase64());
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            String suffix = suffixFromMimeType(request.getResultMimeType(), sourceFile.getSuffix());
            MultimediaFile translatedFile = multimediaFileService.saveTranslatedImage(imageBytes, suffix, subTask.getOwner());

            Language language = resolveLanguage(subTask);
            saveImageTranslationCache(subTask.getImageHash(), sourceFile, language, translatedFile, false);
            saveImageTokenRecord(subTask, sourceFile, translatedFile, false, request.getElapsedMs());

            AiAccountTranslateTaskStatus status = runningTasks.get(subTask.getTaskId());
            if (status != null) {
                status.completeImageSubTask(subTask, translatedFile);
            }
        } catch (Exception e) {
            turboFlowAssignments.remove(request.getAssignmentId());
            throw new IllegalStateException("complete turboflow task failed: " + e.getMessage(), e);
        } finally {
            runtimeState.releaseFinishedSlot();
        }
    }

    public void failTurboFlowTask(String token, TurboFlowBridgeFailRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        AiAccountTranslateSubTask subTask = turboFlowAssignments.get(request.getAssignmentId());
        if (subTask == null) {
            return;
        }
        // fail 也必须校验归属，避免错误插件把别的任务重新入队或标记失败。
        if (!account.getId().equals(subTask.getAiAccountId())) {
            throw new IllegalArgumentException("assignment does not belong to account");
        }
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            throw new IllegalArgumentException("assignment does not belong to bridge");
        }
        if (!turboFlowAssignments.remove(request.getAssignmentId(), subTask)) {
            return;
        }
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(subTask.getAiAccountId(), AiAccountRuntimeState::new);
        try {
            AiAccountTranslateTaskStatus status = runningTasks.get(subTask.getTaskId());
            String message = StrUtil.blankToDefault(request.getMessage(), "TurboFlow task failed");
            boolean retryable = request.getRetryable() == null || Boolean.TRUE.equals(request.getRetryable());
            if (retryable && subTask.getAttemptCount().get() < TURBOFLOW_MAX_ATTEMPTS) {
                subTask.retry(message);
                if (status != null) {
                    status.retrySubTask(subTask, message);
                }
                // 失败任务不回普通栈，进入失败优先栈，确保下一轮优先分配。
                pushTurboFlowSubTask(subTask, true);
            } else {
                subTask.fail(message);
                if (status != null) {
                    status.failSubTask(subTask, message);
                }
            }
        } finally {
            runtimeState.releaseFinishedSlot();
        }
    }

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
                subTaskStacksByAccount
                        .computeIfAbsent(subTask.getAiAccountId(), key -> new ConcurrentLinkedDeque<>())
                        .push(subTask);
            }
            status.setProcessing("已拆分AI账号翻译子任务: " + subTasks.size());
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 拆分任务失败: taskId={}", task.getId(), e);
            AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(task.getId(), 0, null, null, null, task.getOwner());
            status.fail("拆分任务失败: " + e.getMessage());
            runningTasks.put(task.getId(), status);
        }
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

    private void executeAccountSubTasks(Long aiAccountId, ConcurrentLinkedDeque<AiAccountTranslateSubTask> subTaskStack) {
        AiAccountRuntimeState runtimeState = accountRuntimeStates.computeIfAbsent(aiAccountId, AiAccountRuntimeState::new);
        AiAccount account;
        try {
            account = aiAccountService.getById(aiAccountId);
        } catch (Exception e) {
            log.error("[AiAccountTranslateTask] 获取AI账号失败: aiAccountId={}", aiAccountId, e);
            failQueuedSubTasks(subTaskStack, "AI账号不存在或不可用: " + aiAccountId);
            cleanupAccountState(aiAccountId, subTaskStack, runtimeState);
            return;
        }

        if (account.getProvider() == AiProvider.TURBOFLOW) {
            return;
        }

        int executableCount = runtimeState.reserveSlots(account, subTaskStack.size());
        int unusedReservations = 0;
        for (int i = 0; i < executableCount; i++) {
            AiAccountTranslateSubTask subTask = subTaskStack.poll();
            if (subTask == null) {
                unusedReservations++;
                continue;
            }
            executeSubTask(runtimeState, subTask);
        }
        if (unusedReservations > 0) {
            runtimeState.releaseUnusedReservations(unusedReservations);
        }
        cleanupAccountState(aiAccountId, subTaskStack, runtimeState);
    }

    private void failQueuedSubTasks(ConcurrentLinkedDeque<AiAccountTranslateSubTask> subTaskStack, String message) {
        AiAccountTranslateSubTask subTask;
        while ((subTask = subTaskStack.poll()) != null) {
            AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
            if (taskStatus == null) {
                continue;
            }
            subTask.fail(message);
            taskStatus.failPendingSubTask(subTask, message);
        }
    }

    private void cleanupAccountState(Long aiAccountId, ConcurrentLinkedDeque<AiAccountTranslateSubTask> subTaskStack,
                                     AiAccountRuntimeState runtimeState) {
        if (subTaskStack.isEmpty() && runtimeState.getInFlightCount() == 0) {
            subTaskStacksByAccount.remove(aiAccountId, subTaskStack);
            accountRuntimeStates.remove(aiAccountId, runtimeState);
        }
    }

    private void executeSubTask(AiAccountRuntimeState runtimeState, AiAccountTranslateSubTask subTask) {
        AiAccountTranslateTaskStatus taskStatus = runningTasks.get(subTask.getTaskId());
        if (taskStatus == null) {
            runtimeState.releaseFinishedSlot();
            return;
        }

        taskStatus.startSubTask(subTask);
        try {
            subTask.start();
            // TODO 接入新的 AI 账号翻译执行流程。
            subTask.complete();
            taskStatus.completeSubTask(subTask);
        } catch (Exception e) {
            subTask.fail(e.getMessage());
            taskStatus.failSubTask(subTask, e.getMessage());
            log.error("[AiAccountTranslateTask] 子任务执行失败: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), e);
        } finally {
            runtimeState.releaseFinishedSlot();
        }
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

    private AiAccount resolveTurboFlowAccount(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("missing bridge token");
        }
        return aiAccountService.findAvailableAccounts(AiProvider.TURBOFLOW).stream()
                .filter(account -> token.equals(account.getApiKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid TurboFlow bridge token"));
    }

    private String normalizeBridgeId(String bridgeId) {
        return StrUtil.isBlank(bridgeId) ? "unknown" : bridgeId.trim();
    }

    private void reclaimExpiredTurboFlowAssignments(Long aiAccountId,
                                                    AiAccountRuntimeState runtimeState) {
        LocalDateTime now = LocalDateTime.now();
        for (AiAccountTranslateTaskStatus status : runningTasks.values()) {
            for (AiAccountTranslateSubTask subTask : status.getSubTasks().values()) {
                if (!aiAccountId.equals(subTask.getAiAccountId()) || !subTask.isLeaseExpired(now)) {
                    continue;
                }
                String assignmentId = subTask.getAssignmentId();
                if (assignmentId != null) {
                    turboFlowAssignments.remove(assignmentId);
                }
                subTask.retry("TurboFlow lease expired");
                status.retrySubTask(subTask, "TurboFlow lease expired");
                // lease 过期等同于一次可重试失败，也放入失败优先栈。
                pushTurboFlowSubTask(subTask, true);
                runtimeState.releaseFinishedSlot();
            }
        }
    }

    private boolean isEmpty(ConcurrentLinkedDeque<AiAccountTranslateSubTask> stack) {
        return stack == null || stack.isEmpty();
    }

    private AiAccountTranslateSubTask pollTurboFlowSubTask(ConcurrentLinkedDeque<AiAccountTranslateSubTask> failedStack,
                                                           ConcurrentLinkedDeque<AiAccountTranslateSubTask> stack) {
        // 分配顺序：失败优先栈 -> 普通栈。两者都用 push/poll，表现为栈顶优先。
        AiAccountTranslateSubTask subTask = failedStack == null ? null : failedStack.poll();
        if (subTask != null) {
            return subTask;
        }
        return stack == null ? null : stack.poll();
    }

    private void pushTurboFlowSubTask(AiAccountTranslateSubTask subTask, boolean failedFirst) {
        ConcurrentMap<Long, ConcurrentLinkedDeque<AiAccountTranslateSubTask>> target =
                failedFirst ? failedSubTaskStacksByAccount : subTaskStacksByAccount;
        target.computeIfAbsent(subTask.getAiAccountId(), key -> new ConcurrentLinkedDeque<>())
                .push(subTask);
    }

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
            log.warn("[TurboFlowBridge] cache lookup failed: taskId={}, subTaskId={}",
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

    private String toMimeType(String suffix) {
        if (StrUtil.isBlank(suffix)) {
            return "image/png";
        }
        String normalized = suffix.toLowerCase();
        return "image/" + ("jpg".equals(normalized) ? "jpeg" : normalized);
    }

    private String suffixFromMimeType(String mimeType, String fallback) {
        if (StrUtil.isBlank(mimeType)) {
            return StrUtil.blankToDefault(fallback, "png");
        }
        String lower = mimeType.toLowerCase();
        if (lower.contains("jpeg")) {
            return "jpg";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        return "png";
    }

    private byte[] decodeBase64Image(String value) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("result image is empty");
        }
        String base64 = value;
        int comma = value.indexOf(',');
        if (comma >= 0) {
            base64 = value.substring(comma + 1);
        }
        return Base64.getDecoder().decode(base64);
    }

    private void saveImageTranslationCache(String imageHash, MultimediaFile sourceFile, Language language,
                                           MultimediaFile translatedFile, boolean skipped) {
        try {
            if (StrUtil.isBlank(imageHash)) {
                return;
            }
            imageTranslationCacheRepository.save(ImageTranslationCache.builder()
                    .imageHash(imageHash)
                    .sourceFile(sourceFile)
                    .language(language)
                    .translatedFile(translatedFile)
                    .skipped(skipped)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("[TurboFlowBridge] image cache already exists: hash={}", imageHash);
        }
    }

    private void saveImageTokenRecord(AiAccountTranslateSubTask subTask, MultimediaFile sourceFile,
                                      MultimediaFile translatedFile, boolean cacheHit, Long elapsedMs) {
        int maxDim = sourceFile != null ? Math.max(sourceFile.getWidth(), sourceFile.getHeight()) : 512;
        if (maxDim <= 0) {
            maxDim = 512;
        }
        int promptTokens = 718;
        int completionTokens = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
        saveTokenUsageRecord(subTask, TranslationContentType.IMAGE, cacheHit, null, null,
                sourceFile != null ? sourceFile.getRelativePath() : null,
                translatedFile != null ? translatedFile.getRelativePath() : null,
                promptTokens, completionTokens, 0, elapsedMs, translatedFile != null);
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
            log.debug("[TurboFlowBridge] token usage already exists: taskId={}, hash={}",
                    subTask.getTaskId(), subTask.getContentKey());
        } catch (Exception e) {
            log.warn("[TurboFlowBridge] save token usage failed: taskId={}, hash={}",
                    subTask.getTaskId(), subTask.getContentKey(), e);
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

    private enum AiAccountTranslateSubTaskType {
        TEXT, HTML, IMAGE
    }

    private enum AiAccountTranslateSubTaskState {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    private static class AiAccountRuntimeState {

        private final Long aiAccountId;
        private final AtomicInteger inFlightCount = new AtomicInteger(0);
        private LocalDate dayWindowStart = LocalDate.now();
        private LocalDateTime minuteWindowStart = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        private int dayUsed;
        private int minuteUsed;

        private AiAccountRuntimeState(Long aiAccountId) {
            this.aiAccountId = aiAccountId;
        }

        private synchronized int reserveSlots(AiAccount account, int pendingCount) {
            if (pendingCount <= 0) {
                return 0;
            }

            AiRateLimitMode mode = account.getRateLimitMode() == null
                    ? AiRateLimitMode.CONCURRENCY
                    : account.getRateLimitMode();
            int available;
            if (mode == AiRateLimitMode.RPD_RPM) {
                refreshWindows();
                int requestsPerDay = positiveOrZero(account.getRequestsPerDay());
                int requestsPerMinute = positiveOrZero(account.getRequestsPerMinute());
                available = Math.min(requestsPerDay - dayUsed, requestsPerMinute - minuteUsed);
            } else {
                int maxConcurrency = account.getMaxConcurrency() == null
                        ? DEFAULT_MAX_CONCURRENCY
                        : Math.max(DEFAULT_MAX_CONCURRENCY, account.getMaxConcurrency());
                available = maxConcurrency - inFlightCount.get();
            }

            int reserved = Math.min(pendingCount, Math.max(available, 0));
            if (reserved <= 0) {
                return 0;
            }

            inFlightCount.addAndGet(reserved);
            if (mode == AiRateLimitMode.RPD_RPM) {
                dayUsed += reserved;
                minuteUsed += reserved;
            }
            return reserved;
        }

        private synchronized void releaseUnusedReservations(int count) {
            for (int i = 0; i < count; i++) {
                releaseFinishedSlot();
            }
        }

        private void releaseFinishedSlot() {
            int current = inFlightCount.decrementAndGet();
            if (current < 0) {
                inFlightCount.compareAndSet(current, 0);
            }
        }

        private int getInFlightCount() {
            return inFlightCount.get();
        }

        private void refreshWindows() {
            LocalDate today = LocalDate.now();
            if (!today.equals(dayWindowStart)) {
                dayWindowStart = today;
                dayUsed = 0;
            }

            LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            if (!currentMinute.equals(minuteWindowStart)) {
                minuteWindowStart = currentMinute;
                minuteUsed = 0;
            }
        }

        private int positiveOrZero(Integer value) {
            return value == null ? 0 : Math.max(0, value);
        }
    }

    @Getter
    private static class AiAccountTranslateSubTask {

        private final String subTaskId;
        private final Long taskId;
        private final AiAccountTranslateSubTaskType type;
        private final String contentKey;
        private final String content;
        private final String productId;
        private final String countryId;
        private final String languageId;
        private final Long aiAccountId;
        private volatile SystemUser owner;
        private volatile String imageHash;
        private volatile String assignmentId;
        private volatile String assignedBridgeId;
        private volatile LocalDateTime leaseUntil;
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private volatile AiAccountTranslateSubTaskState state = AiAccountTranslateSubTaskState.PENDING;
        private volatile String message;

        private AiAccountTranslateSubTask(Long taskId, AiAccountTranslateSubTaskType type, String contentKey,
                                          String content, TranslateByAIRequest request) {
            this.taskId = taskId;
            this.type = type;
            this.contentKey = contentKey;
            this.content = content;
            this.productId = request.getProductId();
            this.countryId = request.getCountryId();
            this.languageId = request.getLanguageId();
            if (StrUtil.isBlank(request.getAiAccountId())) {
                throw new IllegalArgumentException("AI账号不能为空");
            }
            this.aiAccountId = Long.parseLong(request.getAiAccountId());
            this.subTaskId = taskId + ":" + type + ":" + contentKey;
        }

        private static AiAccountTranslateSubTask text(Long taskId, String hash, String text, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.TEXT, hash, text, request);
        }

        private static AiAccountTranslateSubTask html(Long taskId, String hash, String html, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.HTML, hash, html, request);
        }

        private static AiAccountTranslateSubTask image(Long taskId, String imageId, TranslateByAIRequest request) {
            return new AiAccountTranslateSubTask(taskId, AiAccountTranslateSubTaskType.IMAGE, imageId, imageId, request);
        }

        private void start() {
            this.state = AiAccountTranslateSubTaskState.PROCESSING;
            this.message = null;
        }

        private void dispatch(String bridgeId, String assignmentId, LocalDateTime leaseUntil) {
            this.assignmentId = assignmentId;
            this.assignedBridgeId = bridgeId;
            this.leaseUntil = leaseUntil;
            this.state = AiAccountTranslateSubTaskState.PROCESSING;
            this.message = null;
            this.attemptCount.incrementAndGet();
        }

        private boolean isAssignedTo(String bridgeId, String assignmentId) {
            return assignmentId != null
                    && assignmentId.equals(this.assignmentId)
                    && (StrUtil.isBlank(bridgeId) || bridgeId.equals(this.assignedBridgeId));
        }

        private boolean isLeaseExpired(LocalDateTime now) {
            return state == AiAccountTranslateSubTaskState.PROCESSING
                    && leaseUntil != null
                    && now.isAfter(leaseUntil);
        }

        private void retry(String message) {
            this.state = AiAccountTranslateSubTaskState.PENDING;
            this.message = message;
            this.assignmentId = null;
            this.assignedBridgeId = null;
            this.leaseUntil = null;
        }

        private void complete() {
            this.state = AiAccountTranslateSubTaskState.COMPLETED;
            this.message = null;
            this.assignmentId = null;
            this.assignedBridgeId = null;
            this.leaseUntil = null;
        }

        private void fail(String message) {
            this.state = AiAccountTranslateSubTaskState.FAILED;
            this.message = message;
            this.assignmentId = null;
            this.assignedBridgeId = null;
            this.leaseUntil = null;
        }

        private void setOwner(SystemUser owner) {
            this.owner = owner;
        }

        private void setImageHash(String imageHash) {
            this.imageHash = imageHash;
        }

        private MultimediaFile resolveSourceFile(IMultimediaFileService multimediaFileService) {
            if (type != AiAccountTranslateSubTaskType.IMAGE) {
                throw new IllegalStateException("not an image task");
            }
            return multimediaFileService.getById(Long.parseLong(content));
        }
    }

    @Getter
    private static class AiAccountTranslateTaskStatus {

        private final Long taskId;
        private final int totalSubTaskCount;
        private final AtomicInteger processingSubTaskCount = new AtomicInteger(0);
        private final AtomicInteger completedSubTaskCount = new AtomicInteger(0);
        private final AtomicInteger failedSubTaskCount = new AtomicInteger(0);
        private final ConcurrentMap<String, AiAccountTranslateSubTask> subTasks = new ConcurrentHashMap<>();
        private final Product product;
        private final Language language;
        private final Country country;
        private final SystemUser owner;
        private final ConcurrentMap<String, String> translatedTextMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, MultimediaFile> translatedImageMap = new ConcurrentHashMap<>();
        private final AtomicBoolean finalizing = new AtomicBoolean(false);
        private final AtomicBoolean finalized = new AtomicBoolean(false);
        private volatile String translatedHtml;
        private volatile TaskState state = TaskState.PROCESSING;
        private volatile int progress;
        private volatile String message;
        private volatile LocalDateTime updateTime = LocalDateTime.now();

        private AiAccountTranslateTaskStatus(Long taskId, int totalSubTaskCount,
                                             Product product, Language language, Country country, SystemUser owner) {
            this.taskId = taskId;
            this.totalSubTaskCount = totalSubTaskCount;
            this.product = product;
            this.language = language;
            this.country = country;
            this.owner = owner;
            this.progress = totalSubTaskCount == 0 ? 100 : 0;
            this.message = totalSubTaskCount == 0 ? "没有需要翻译的内容" : null;
        }

        private void addSubTask(AiAccountTranslateSubTask subTask) {
            subTasks.put(subTask.getSubTaskId(), subTask);
            touch();
        }

        private void setProcessing(String message) {
            this.state = TaskState.PROCESSING;
            this.message = message;
            touch();
        }

        private void startSubTask(AiAccountTranslateSubTask subTask) {
            processingSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            setProcessing("正在执行AI账号翻译子任务");
        }

        private void dispatchSubTask(AiAccountTranslateSubTask subTask) {
            processingSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            setProcessing("TurboFlow image task dispatched");
        }

        private void completeSubTask(AiAccountTranslateSubTask subTask) {
            subTask.complete();
            decrementProcessingIfNeeded();
            completedSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            refreshProgress();
        }

        private void completeTextSubTask(AiAccountTranslateSubTask subTask, String translatedText) {
            translatedTextMap.put(subTask.getContentKey(), translatedText);
            completeSubTask(subTask);
        }

        private void completeHtmlSubTask(AiAccountTranslateSubTask subTask, String translatedHtml) {
            this.translatedHtml = translatedHtml;
            completeSubTask(subTask);
        }

        private void completeImageSubTask(AiAccountTranslateSubTask subTask, MultimediaFile translatedFile) {
            translatedImageMap.put(subTask.getContent(), translatedFile);
            completeSubTask(subTask);
        }

        private void retrySubTask(AiAccountTranslateSubTask subTask, String message) {
            decrementProcessingIfNeeded();
            subTasks.put(subTask.getSubTaskId(), subTask);
            this.message = message;
            refreshProgress();
        }

        private void failSubTask(AiAccountTranslateSubTask subTask, String message) {
            decrementProcessingIfNeeded();
            failedSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            this.message = message;
            refreshProgress();
        }

        private void failPendingSubTask(AiAccountTranslateSubTask subTask, String message) {
            failedSubTaskCount.incrementAndGet();
            subTasks.put(subTask.getSubTaskId(), subTask);
            this.message = message;
            refreshProgress();
        }

        private void complete() {
            this.state = TaskState.COMPLETED;
            this.progress = 100;
            this.message = "AI账号翻译任务完成";
            touch();
        }

        private void fail(String message) {
            this.state = TaskState.FAILED;
            this.progress = 100;
            this.message = message;
            touch();
        }

        private void decrementProcessingIfNeeded() {
            int current;
            do {
                current = processingSubTaskCount.get();
                if (current <= 0) {
                    return;
                }
            } while (!processingSubTaskCount.compareAndSet(current, current - 1));
        }

        private void refreshProgress() {
            int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
            this.progress = totalSubTaskCount == 0 ? 100 : Math.min(100, finished * 100 / totalSubTaskCount);
            if (finished >= totalSubTaskCount) {
                if (failedSubTaskCount.get() > 0) {
                    this.state = TaskState.FAILED;
                    this.message = "AI账号翻译子任务失败: " + failedSubTaskCount.get();
                } else {
                    this.state = TaskState.PROCESSING;
                    this.message = "AI account translate subtasks complete, assembling product";
                }
            }
            touch();
        }

        private boolean isFinished() {
            return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
        }

        private boolean isReadyToFinalize() {
            int finished = completedSubTaskCount.get() + failedSubTaskCount.get();
            return product != null
                    && !isFinished()
                    && finished >= totalSubTaskCount
                    && failedSubTaskCount.get() == 0
                    && !finalized.get();
        }

        private boolean markFinalizing() {
            return finalizing.compareAndSet(false, true);
        }

        private void markFinalized() {
            finalized.set(true);
        }

        private void touch() {
            this.updateTime = LocalDateTime.now();
        }
    }

    @Getter
    private static class TurboFlowBridgeState {

        private final Long aiAccountId;
        private final String bridgeId;
        private final String version;
        private final boolean flowConnected;
        private final String projectId;
        private final String currentUrl;
        private final boolean busy;
        private final Map<String, Object> accountInfo;
        private final LocalDateTime lastHeartbeatAt;

        private TurboFlowBridgeState(Long aiAccountId, String bridgeId, String version,
                                     boolean flowConnected, String projectId, String currentUrl,
                                     boolean busy, Map<String, Object> accountInfo) {
            this.aiAccountId = aiAccountId;
            this.bridgeId = bridgeId;
            this.version = version;
            this.flowConnected = flowConnected;
            this.projectId = projectId;
            this.currentUrl = currentUrl;
            this.busy = busy;
            this.accountInfo = accountInfo;
            this.lastHeartbeatAt = LocalDateTime.now();
        }

        private static TurboFlowBridgeState from(Long aiAccountId, TurboFlowBridgeHeartbeatRequest request) {
            return new TurboFlowBridgeState(
                    aiAccountId,
                    StrUtil.blankToDefault(request.getBridgeId(), "unknown"),
                    request.getVersion(),
                    Boolean.TRUE.equals(request.getFlowConnected()),
                    request.getProjectId(),
                    request.getCurrentUrl(),
                    Boolean.TRUE.equals(request.getBusy()),
                    request.getAccountInfo());
        }

        private static TurboFlowBridgeState from(Long aiAccountId, TurboFlowBridgePollRequest request) {
            return new TurboFlowBridgeState(
                    aiAccountId,
                    StrUtil.blankToDefault(request.getBridgeId(), "unknown"),
                    request.getVersion(),
                    Boolean.TRUE.equals(request.getFlowConnected()),
                    request.getProjectId(),
                    request.getCurrentUrl(),
                    false,
                    request.getAccountInfo());
        }
    }
}
