package cn.v7soft.admin.task.provider;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeFailRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskType;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.ImagePolicyCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import cn.v7soft.dao.repositories.primary.ImagePolicyCacheRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TurboFlow 浏览器插件的 Provider 实现。
 * <p>
 * 工作模式为被动分发：executeSubTask 仅将子任务存入内部队列，
 * 实际执行由 TurboFlow 插件通过 HTTP poll 拉取任务触发。
 * <p>
 * 计费职责已迁移到 AiAccountTranslateTask：本 Provider 仅在 SubTaskResult 中回传 token 用量，
 * 由 TranslateTaskCallbackAdapter -> AiAccountTranslateTask 统一持久化。
 */
@Slf4j
@Component
public class TurboFlowBridgeProvider implements TranslateProvider {

    /**
     * 任务分配后允许的执行时间。
     * 已配合 plugin 端 90 秒超时；3 分钟覆盖 plugin 超时 + 网络重试 + sync 周期，保证及时回收。
     */
    private static final int TURBOFLOW_LEASE_MINUTES = 3;

    private final IAiAccountService aiAccountService;
    private final IMultimediaFileService multimediaFileService;
    private final ILanguageService languageService;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final ImagePolicyCacheRepository imagePolicyCacheRepository;
    private final AiTokenUsageRecordRepository usageRecordRepository;
    private final GeminiTranslateService geminiTranslateService;
    private final ThreadPoolTaskExecutor executor;
    private final RateLimiter rateLimiter;

    public TurboFlowBridgeProvider(
            IAiAccountService aiAccountService,
            IMultimediaFileService multimediaFileService,
            ILanguageService languageService,
            ImageTranslationCacheRepository imageTranslationCacheRepository,
            AiTokenUsageRecordRepository usageRecordRepository,
            ImagePolicyCacheRepository imagePolicyCacheRepository,
            GeminiTranslateService geminiTranslateService,
            @Qualifier("translationExecutor") ThreadPoolTaskExecutor executor,
            RateLimiter geminiRateLimiter) {
        this.aiAccountService = aiAccountService;
        this.multimediaFileService = multimediaFileService;
        this.languageService = languageService;
        this.imageTranslationCacheRepository = imageTranslationCacheRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.imagePolicyCacheRepository = imagePolicyCacheRepository;
        this.geminiTranslateService = geminiTranslateService;
        this.executor = executor;
        this.rateLimiter = geminiRateLimiter;
    }

    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> internalQueues = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> priorityInternalQueues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AiAccountTranslateSubTask> assignments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> completedAssignments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TurboFlowBridgeState> bridgeStates = new ConcurrentHashMap<>();

    private volatile TranslateProviderCallback callback;

    @Override
    public AiProvider getProviderType() {
        return AiProvider.TURBOFLOW_GEMINI;
    }

    @Override
    public void setCallback(TranslateProviderCallback callback) {
        this.callback = callback;
    }

    @Override
    public int estimateSubTaskCredits(AiAccount account, AiAccountTranslateSubTask subTask) {
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            return TokenCostCalculator.estimateCredits(0, TokenCostCalculator.estimateImageTokens(), account);
        }
        int textTokens = TokenCostCalculator.estimateTextTokens(subTask.getContent());
        return TokenCostCalculator.estimateCredits(textTokens, 0, account);
    }

    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            if (subTask.isSkipped()) {
                log.warn("[TurboFlowBridge] skipped image subtask should not be queued; completing via callback: subTaskId={}",
                        subTask.getSubTaskId());
                callback.onSubTaskCompleted(subTask, SubTaskResult.builder().build());
                return;
            }
            boolean priority = subTask.consumePriorityRetry();
            ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> targetQueues =
                    priority ? priorityInternalQueues : internalQueues;
            ConcurrentLinkedQueue<AiAccountTranslateSubTask> queue = targetQueues
                    .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>());
            queue.offer(subTask);
            log.debug("[TurboFlowBridge] image subtask queued for bridge poll: taskId={}, subTaskId={}, aiAccountId={}, priority={}, queueSize={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getAiAccountId(),
                    priority, queue.size());
        } else {
            subTask.start();
            subTask.getAttemptCount().incrementAndGet();
            log.debug("[TurboFlowBridge] text/html subtask submitted to Gemini fallback: taskId={}, subTaskId={}, type={}, attempt={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                    subTask.getAttemptCount().get());
            executor.submit(() -> executeTextViaGemini(subTask));
        }
    }

    private void executeTextViaGemini(AiAccountTranslateSubTask subTask) {
        try {
            log.debug("[TurboFlowBridge] waiting for rate limiter: taskId={}, subTaskId={}, type={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType());
            RateLimiter.waitForPermission(rateLimiter);
            AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
            Language language = languageService.getById(Long.parseLong(subTask.getLanguageId()));
            String langName = language.getName();
            log.debug("[TurboFlowBridge] executing text/html via Gemini: taskId={}, subTaskId={}, type={}, aiAccountId={}, language={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), account.getId(), langName);

            AtomicReference<GeminiTranslateService.TokenUsage> usageRef = new AtomicReference<>();
            String translated;
            TranslationContentType contentType;
            if (subTask.getType() == AiAccountTranslateSubTaskType.HTML) {
                translated = geminiTranslateService.translateHtmlRaw(subTask.getContent(), langName, usageRef::set);
                contentType = TranslationContentType.HTML;
            } else {
                translated = geminiTranslateService.translateTextRaw(subTask.getContent(), langName, usageRef::set);
                contentType = TranslationContentType.TEXT;
            }

            GeminiTranslateService.TokenUsage usage = usageRef.get();
            int prompt = usage != null ? TranslateProviderSupport.safeInt(usage.getPromptTokens()) : 0;
            int completion = usage != null ? TranslateProviderSupport.safeInt(usage.getCompletionTokens()) : 0;
            int thinking = usage != null ? TranslateProviderSupport.safeInt(usage.getThinkingTokens()) : 0;
            BigDecimal cost = TokenCostCalculator.calculateCost(contentType, account, prompt, completion, thinking);

            SubTaskResult.SubTaskResultBuilder resultBuilder = SubTaskResult.builder()
                    .elapsedMs(usage != null ? usage.getElapsedMs() : null)
                    .actualPromptTokens(prompt)
                    .actualCompletionTokens(completion)
                    .actualThinkingTokens(thinking)
                    .businessPromptTokens(prompt)
                    .businessCompletionTokens(completion)
                    .businessThinkingTokens(thinking)
                    .businessCredits(TokenCostCalculator.usdToCredits(cost));
            if (contentType == TranslationContentType.HTML) {
                resultBuilder.translatedHtml(translated);
            } else {
                resultBuilder.translatedText(translated);
            }
            log.debug("[TurboFlowBridge] text/html Gemini fallback completed: taskId={}, subTaskId={}, type={}, actualTokens={}, businessCredits={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                    prompt + completion + thinking, TokenCostCalculator.usdToCredits(cost));
            callback.onSubTaskCompleted(subTask, resultBuilder.build());
        } catch (Exception e) {
            log.error("[TurboFlowBridge] text translation failed: subTaskId={}", subTask.getSubTaskId(), e);
            boolean billable = GeminiOfficialProvider.isBillableError(e);
            SubTaskResult partialResult = null;
            if (billable) {
                AiAccount acc = aiAccountService.getById(subTask.getAiAccountId());
                TranslationContentType ct = subTask.getType() == AiAccountTranslateSubTaskType.HTML
                        ? TranslationContentType.HTML : TranslationContentType.TEXT;
                int est = TokenCostCalculator.estimateTextTokens(subTask.getContent());
                BigDecimal cost = TokenCostCalculator.calculateCost(ct, acc, est, est, 0);
                partialResult = SubTaskResult.builder()
                        .businessPromptTokens(est)
                        .businessCompletionTokens(est)
                        .businessCredits(TokenCostCalculator.usdToCredits(cost))
                        .build();
            }
            callback.onSubTaskFailed(subTask, e.getMessage(), !billable, partialResult, null);
        }
    }

    /**
     * 插件轮询获取任务。
     * <p>
     * 设计原则：服务端不判断 bridge 能力（flowConnected/busy 由 bridge 自行管理）：
     * 只要 bridge 来 poll 就按 FIFO 派发任务。如果 bridge 当前无法执行（如 Flow tab 关闭、reCAPTCHA blocked），
     * 应在插件端自行决定不发起 poll，直到环境恢复。
     * <p>
     * 流程：鉴权 → 从队列取子任务 → 读图片 → 查缓存 → 分配 assignment → 返回图片数据
     */
    public TurboFlowBridgeTaskResponse pollTask(String token, TurboFlowBridgePollRequest request) {
        // 1. 通过 Bearer token 鉴权，找到所有匹配的 AiAccount
        String bridgeId = normalizeBridgeId(request.getBridgeId());
        List<AiAccount> accounts = findAllTurboFlowAccounts(token);
        if (accounts.isEmpty()) {
            log.debug("[TurboFlowBridge] poll rejected: invalid bridge token, bridgeId={}", bridgeId);
            return TurboFlowBridgeTaskResponse.builder()
                    .hasTask(false)
                    .message("invalid bridge token")
                    .build();
        }
        // 记录插件在线状态（仅用于观测，不参与派发决策）
        bridgeStates.put(bridgeId, TurboFlowBridgeState.from(accounts.get(0).getId(), request));
        log.debug("[TurboFlowBridge] poll received: bridgeId={}, matchedAccounts={}, flowConnected={}, busy={}",
                bridgeId, accounts.size(), request.getFlowConnected(), request.getBusy());

        // 2. 遍历所有匹配账号的队列，优先取失败重试任务，普通任务保持 FIFO
        AiAccountTranslateSubTask subTask = null;
        AiAccount account = null;
        for (AiAccount acc : accounts) {
            AiAccountTranslateSubTask candidate = pollQueuedSubTask(acc.getId());
            if (candidate != null) {
                subTask = candidate;
                account = acc;
                break;
            }
        }
        if (subTask == null || account == null) {
            log.debug("[TurboFlowBridge] poll found no queued image task: bridgeId={}, checkedAccounts={}",
                    bridgeId, accounts.size());
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
        }

        // 防御：TEXT/HTML 已在 executeSubTask 阶段由 Gemini 处理，理论上不会出现在队列中
        if (subTask.getType() != AiAccountTranslateSubTaskType.IMAGE) {
            log.warn("[TurboFlowBridge] unexpected non-image task in queue: {}", subTask.getSubTaskId());
            callback.onSubTaskFailed(subTask, "non-image task in TurboFlow queue", true, null, null);
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("non-image task skipped").build();
        }

        // 检查父任务是否仍然存活；不活跃时通知失败以释放槽位
        if (!callback.isTaskActive(subTask.getTaskId())) {
            log.debug("[TurboFlowBridge] polled subtask parent is inactive: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId());
            callback.onSubTaskFailed(subTask, "parent task no longer active", false, null, null);
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("task missing").build();
        }

        try {
            // 3. 读取源图片并计算哈希
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            byte[] imageBytes = TranslateProviderSupport.readImageBytes(multimediaFileService, sourceFile);
            String imageHash = DigestUtil.sha256Hex(imageBytes);
            subTask.setImageHash(imageHash);
            log.debug("[TurboFlowBridge] source image prepared for bridge dispatch: taskId={}, subTaskId={}, imageId={}, imageHash={}, bytes={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), sourceFile.getId(), imageHash, imageBytes.length);

            // 4. 二次缓存检查：同图同语言的成功译图优先，其次是跨语言政策缓存。
            Optional<ImageTranslationCache> translationCache = imageTranslationCacheRepository
                    .findByImageHashAndLanguageId(imageHash, Long.parseLong(subTask.getLanguageId()));
            Language cacheLanguage = resolveLanguage(subTask);
            if (translationCache.isPresent()
                    && !translationCache.get().isSkipped()
                    && translationCache.get().getTranslatedFile() != null) {
                MultimediaFile translatedFile = translationCache.get().getTranslatedFile();
                callback.onSubTaskCompleted(subTask,
                        buildCachedImageResult(account, imageHash, cacheLanguage, sourceFile, translatedFile, null));
                log.debug("[TurboFlowBridge] translated image cache hit during bridge poll: taskId={}, subTaskId={}, imageHash={}, language={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), imageHash, cacheLanguage.getName());
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("translation cache hit").build();
            }

            Optional<ImagePolicyCache> policyCache = imagePolicyCacheRepository.findByImageHash(imageHash);
            if (policyCache.isPresent()) {
                String reason = policyCache.get().getReason();
                callback.onSubTaskCompleted(subTask,
                        buildCachedImageResult(account, imageHash, cacheLanguage, sourceFile, null, reason));
                log.warn("[TurboFlowBridge] policy cache hit, keeping original image: taskId={}, subTaskId={}, imageHash={}, reason={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), imageHash, reason);
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("policy cache hit").build();
            }

            if (translationCache.isPresent()) {
                callback.onSubTaskCompleted(subTask,
                        buildCachedImageResult(account, imageHash, cacheLanguage, sourceFile, null, null));
                log.debug("[TurboFlowBridge] skipped image cache hit during bridge poll: taskId={}, subTaskId={}, imageHash={}, language={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), imageHash, cacheLanguage.getName());
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("skipped cache hit").build();
            }
            log.debug("[TurboFlowBridge] image cache miss during bridge poll: taskId={}, subTaskId={}, imageId={}, imageHash={}, languageId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), sourceFile.getId(), imageHash, subTask.getLanguageId());

            // 6. 分配 assignmentId + lease，跟踪该子任务直到 complete/fail/expire
            String assignmentId = UUID.randomUUID().toString();
            LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(TURBOFLOW_LEASE_MINUTES);
            subTask.dispatch(bridgeId, assignmentId, leaseUntil);
            assignments.put(assignmentId, subTask);
            log.debug("[TurboFlowBridge] assignment created: taskId={}, subTaskId={}, assignmentId={}, bridgeId={}, leaseUntil={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), assignmentId, bridgeId, leaseUntil);

            // 7. 构建响应：图片 Base64 + 目标语言 + lease 信息
            Language language = resolveLanguage(subTask);

            return TurboFlowBridgeTaskResponse.builder()
                    .hasTask(true)
                    .taskId(subTask.getTaskId())
                    .subTaskId(subTask.getSubTaskId())
                    .assignmentId(assignmentId)
                    .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                    .fileName(sourceFile.getName() + "." + sourceFile.getSuffix())
                    .mimeType(TranslateProviderSupport.toMimeType(sourceFile.getSuffix()))
                    .model(account.getModel())
                    .targetLanguage(language.getName())
                    .targetLanguageCode(language.getCode())
                    .sourceWidth(sourceFile.getWidth())
                    .sourceHeight(sourceFile.getHeight())
                    .leaseUntil(leaseUntil)
                    .build();
        } catch (Exception e) {
            // 分发失败，通知 adapter 进入重试流程
            callback.onSubTaskFailed(subTask, "dispatch failed: " + e.getMessage(), true, null, "DISPATCH_FAILED");
            log.error("[TurboFlowBridge] 分发任务失败: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), e);
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("dispatch failed: " + e.getMessage()).build();
        }
    }

    /**
     * 插件上报翻译完成。
     * 验证归属 → 解码翻译图片 → 保存文件和缓存 → 计算 token 用量 → 通过 callback 通知完成
     */
    public void completeTask(String token, TurboFlowBridgeCompleteRequest request) {
        List<AiAccount> accounts = findAllTurboFlowAccounts(token);
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("invalid TurboFlow bridge token");
        }
        AiAccountTranslateSubTask subTask = assignments.get(request.getAssignmentId());
        if (subTask == null) {
            if (completedAssignments.containsKey(request.getAssignmentId())
                    || isPersistedPolicyCompletion(request)) {
                log.debug("[TurboFlowBridge] duplicate policy completion acknowledged: assignmentId={}, imageHash={}",
                        request.getAssignmentId(), request.getImageHash());
                return;
            }
            throw new IllegalArgumentException("assignment not found or expired");
        }
        // 从匹配的账号中找出拥有该 subtask 的账号
        AiAccount account = accounts.stream()
                .filter(a -> a.getId().equals(subTask.getAiAccountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assignment does not belong to account"));
        if (StrUtil.isNotBlank(request.getImageHash())
                && !request.getImageHash().equals(subTask.getImageHash())) {
            throw new IllegalArgumentException("policy fallback image hash does not match assignment");
        }
        log.debug("[TurboFlowBridge] complete received: taskId={}, subTaskId={}, assignmentId={}, aiAccountId={}, elapsedMs={}",
                subTask.getTaskId(), subTask.getSubTaskId(), request.getAssignmentId(),
                account.getId(), request.getElapsedMs());
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            if (StrUtil.isNotBlank(request.getBridgeId())
                    && subTask.getAssignedBridgeId() != null
                    && !request.getBridgeId().equals(subTask.getAssignedBridgeId())) {
                log.warn("[TurboFlowBridge] complete: bridgeId changed, rebinding: assignmentId={}, oldBridgeId={}, newBridgeId={}",
                        request.getAssignmentId(), subTask.getAssignedBridgeId(), request.getBridgeId());
                subTask.rebindAssignedBridge(request.getBridgeId());
            }
            if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
                throw new IllegalArgumentException("assignment does not belong to bridge");
            }
        }
        // 原子移除 assignment，防止重复 complete
        if (!assignments.remove(request.getAssignmentId(), subTask)) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        try {
            if (Boolean.TRUE.equals(request.getPolicyFallback())) {
                if (!"INVALID_ARGUMENT".equals(request.getPolicyFallbackStatus())) {
                    throw new IllegalArgumentException("unsupported policy fallback status");
                }
                MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
                String reason = StrUtil.blankToDefault(
                        request.getPolicyFallbackReason(), request.getPolicyFallbackStatus());
                saveImagePolicyCache(subTask.getImageHash(), sourceFile,
                        request.getPolicyFallbackStatus(), reason);

                int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
                if (maxDim <= 0) maxDim = 512;
                int promptTokens = TokenCostCalculator.imageBusinessPromptTokens(maxDim);
                int completionTokens = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
                int businessCredits = TokenCostCalculator.usdToCredits(
                        TokenCostCalculator.calculateCost(TranslationContentType.IMAGE, account,
                                promptTokens, completionTokens, 0));
                SubTaskResult result = SubTaskResult.builder()
                        .elapsedMs(request.getElapsedMs())
                        .policyFallbackReason(reason)
                        .businessPromptTokens(promptTokens)
                        .businessCompletionTokens(completionTokens)
                        .businessCredits(businessCredits)
                        .actualPromptTokens(promptTokens)
                        .actualCompletionTokens(completionTokens)
                        .build();
                log.warn("[TurboFlowBridge] image kept unchanged after upload policy rejection: taskId={}, subTaskId={}, imageHash={}, status={}, reason={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), subTask.getImageHash(),
                        request.getPolicyFallbackStatus(), reason);
                callback.onSubTaskCompleted(subTask, result);
                completedAssignments.put(request.getAssignmentId(), LocalDateTime.now());
                return;
            }

            // 解码并保存翻译后的图片
            byte[] imageBytes = decodeBase64Image(request.getResultImageBase64());
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            String suffix = suffixFromMimeType(request.getResultMimeType(), sourceFile.getSuffix());
            MultimediaFile translatedFile = multimediaFileService.saveTranslatedImage(imageBytes, suffix, subTask.getOwner());

            log.debug("[TurboFlowBridge] translated image saved from bridge result: taskId={}, subTaskId={}, sourceImageId={}, translatedFileId={}, bytes={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), sourceFile.getId(), translatedFile.getId(), imageBytes.length);

            // 保存翻译缓存，供后续相同图片+语言直接命中
            Language language = resolveLanguage(subTask);
            saveImageTranslationCache(subTask.getImageHash(), sourceFile, language, translatedFile, false);

            // 计算业务 token 用量（按图片分辨率档位）
            int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
            if (maxDim <= 0) maxDim = 512;
            int promptTokens = 718;
            int completionTokens = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
            int businessCredits = TokenCostCalculator.usdToCredits(
                    TokenCostCalculator.calculateCost(TranslationContentType.IMAGE, account,
                            promptTokens, completionTokens, 0));

            // 将翻译结果和 token 用量打包回传给 adapter
            // TurboFlow 无真实 API 返回的 actual 数据，按 AiAccount 配置与 business 相同
            SubTaskResult result = SubTaskResult.builder()
                    .translatedFile(translatedFile)
                    .elapsedMs(request.getElapsedMs())
                    .resultMimeType(request.getResultMimeType())
                    .businessPromptTokens(promptTokens)
                    .businessCompletionTokens(completionTokens)
                    .businessCredits(businessCredits)
                    .actualPromptTokens(promptTokens)
                    .actualCompletionTokens(completionTokens)
                    .build();
            log.debug("[TurboFlowBridge] completion result built: taskId={}, subTaskId={}, promptTokens={}, completionTokens={}, businessCredits={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), promptTokens, completionTokens, businessCredits);
            callback.onSubTaskCompleted(subTask, result);
            completedAssignments.put(request.getAssignmentId(), LocalDateTime.now());
        } catch (Exception e) {
            callback.onSubTaskFailed(subTask, "complete processing failed: " + e.getMessage(), true, null, "COMPLETE_PROCESSING_FAILED");
            log.error("[TurboFlowBridge] completeTask 处理失败, 已推入重试队列: assignmentId={}",
                    request.getAssignmentId(), e);
            throw new IllegalStateException("complete turboflow task failed: " + e.getMessage(), e);
        }
    }

    /** 插件上报翻译失败。验证归属后通过 callback 通知 adapter（由 adapter 决定重试或标记失败） */
    public void failTask(String token, TurboFlowBridgeFailRequest request) {
        List<AiAccount> accounts = findAllTurboFlowAccounts(token);
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException("invalid TurboFlow bridge token");
        }
        AiAccountTranslateSubTask subTask = assignments.get(request.getAssignmentId());
        if (subTask == null) {
            log.debug("[TurboFlowBridge] fail ignored because assignment is missing: assignmentId={}",
                    request.getAssignmentId());
            return;
        }
        // 从匹配的账号中找出拥有该 subtask 的账号
        accounts.stream()
                .filter(a -> a.getId().equals(subTask.getAiAccountId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assignment does not belong to account"));
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            if (StrUtil.isNotBlank(request.getBridgeId())
                    && subTask.getAssignedBridgeId() != null
                    && !request.getBridgeId().equals(subTask.getAssignedBridgeId())) {
                log.warn("[TurboFlowBridge] fail: bridgeId changed, rebinding: assignmentId={}, oldBridgeId={}, newBridgeId={}",
                        request.getAssignmentId(), subTask.getAssignedBridgeId(), request.getBridgeId());
                subTask.rebindAssignedBridge(request.getBridgeId());
            }
            if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
                throw new IllegalArgumentException("assignment does not belong to bridge");
            }
        }
        if (!assignments.remove(request.getAssignmentId(), subTask)) {
            return;
        }
        String message = StrUtil.blankToDefault(request.getMessage(), "TurboFlow task failed");
        boolean retryable = request.getRetryable() == null || Boolean.TRUE.equals(request.getRetryable());
        String errorCode = request.getErrorCode();
        log.debug("[TurboFlowBridge] fail received: taskId={}, subTaskId={}, assignmentId={}, retryable={}, errorCode={}, message={}",
                subTask.getTaskId(), subTask.getSubTaskId(), request.getAssignmentId(), retryable, errorCode, message);
        callback.onSubTaskFailed(subTask, message, retryable, null, errorCode);
    }

    @Override
    public void onTaskCancelling(Long taskId) {
        // 1. 从内部待 poll 队列移除：尚未分发给插件，不计费
        removeQueuedSubTasks(taskId, priorityInternalQueues);
        removeQueuedSubTasks(taskId, internalQueues);
        // 2. assignments：已分发给插件，但 TurboFlow 免费 AI 不计费
        Iterator<Map.Entry<String, AiAccountTranslateSubTask>> it = assignments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AiAccountTranslateSubTask> entry = it.next();
            AiAccountTranslateSubTask subTask = entry.getValue();
            if (taskId.equals(subTask.getTaskId())) {
                it.remove();
                callback.onSubTaskFailed(subTask, "task cancelled", false, null, null);
            }
        }
    }

    /** 回收 lease 过期的 assignment。由 syncTaskStatus 定时器(5s)调用 */
    @Override
    public void reclaimExpiredAssignments() {
        LocalDateTime now = LocalDateTime.now();
        completedAssignments.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minusHours(24)));
        Iterator<Map.Entry<String, AiAccountTranslateSubTask>> it = assignments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AiAccountTranslateSubTask> entry = it.next();
            AiAccountTranslateSubTask subTask = entry.getValue();
            if (!subTask.isLeaseExpired(now)) {
                continue;
            }
            // 从 assignments 移除并通知过期
            it.remove();
            log.warn("[TurboFlowBridge] lease expired: taskId={}, subTaskId={}, assignmentId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), entry.getKey());
            callback.onSubTaskFailed(subTask, "TurboFlow lease expired", true, null, "LEASE_EXPIRED");
        }
    }

    // --- private helpers ---

    private AiAccountTranslateSubTask pollQueuedSubTask(Long aiAccountId) {
        ConcurrentLinkedQueue<AiAccountTranslateSubTask> priorityQueue = priorityInternalQueues.get(aiAccountId);
        AiAccountTranslateSubTask subTask = priorityQueue == null ? null : priorityQueue.poll();
        if (subTask != null) {
            return subTask;
        }

        ConcurrentLinkedQueue<AiAccountTranslateSubTask> queue = internalQueues.get(aiAccountId);
        return queue == null ? null : queue.poll();
    }

    private void removeQueuedSubTasks(Long taskId,
                                      ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> queues) {
        for (ConcurrentLinkedQueue<AiAccountTranslateSubTask> queue : queues.values()) {
            Iterator<AiAccountTranslateSubTask> it = queue.iterator();
            while (it.hasNext()) {
                AiAccountTranslateSubTask subTask = it.next();
                if (taskId.equals(subTask.getTaskId())) {
                    it.remove();
                    callback.onSubTaskFailed(subTask, "task cancelled", false, null, null);
                }
            }
        }
    }

    private List<AiAccount> findAllTurboFlowAccounts(String token) {
        if (StrUtil.isBlank(token)) {
            return List.of();
        }
        return aiAccountService.findAvailableAccountsByApiKey(AiProvider.TURBOFLOW_GEMINI, token);
    }

    private String normalizeBridgeId(String bridgeId) {
        return StrUtil.isBlank(bridgeId) ? "unknown" : bridgeId.trim();
    }

    private Language resolveLanguage(AiAccountTranslateSubTask subTask) {
        return languageService.getById(Long.parseLong(subTask.getLanguageId()));
    }

    private String suffixFromMimeType(String mimeType, String fallback) {
        if (StrUtil.isBlank(mimeType)) {
            return StrUtil.blankToDefault(fallback, "png");
        }
        String lower = mimeType.toLowerCase();
        if (lower.contains("jpeg")) return "jpg";
        if (lower.contains("webp")) return "webp";
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
            if (StrUtil.isBlank(imageHash)) return;
            imageTranslationCacheRepository.save(ImageTranslationCache.builder()
                    .imageHash(imageHash)
                    .sourceFile(sourceFile)
                    .language(language)
                    .translatedFile(translatedFile)
                    .skipped(skipped)
                    .build());
            log.debug("[TurboFlowBridge] image cache saved: sourceImageId={}, imageHash={}, languageId={}, translatedFileId={}, skipped={}",
                    sourceFile.getId(), imageHash, language.getId(),
                    translatedFile == null ? null : translatedFile.getId(), skipped);
        } catch (DataIntegrityViolationException e) {
            log.debug("[TurboFlowBridge] image cache already exists: sourceImageId={}, imageHash={}, languageId={}",
                    sourceFile.getId(), imageHash, language.getId());
        }
    }

    private SubTaskResult buildCachedImageResult(AiAccount account, String imageHash, Language language,
                                                 MultimediaFile sourceFile, MultimediaFile translatedFile, String policyFallbackReason) {
        int promptTokens;
        int completionTokens;
        int businessCredits;
        Optional<AiTokenUsageRecord> historyOpt = usageRecordRepository
                .findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
                        imageHash, language.getName());
        if (historyOpt.isPresent() && historyOpt.get().getBusinessCredits() > 0) {
            AiTokenUsageRecord history = historyOpt.get();
            promptTokens = history.getBusinessPromptTokens();
            completionTokens = history.getBusinessCompletionTokens();
            businessCredits = history.getBusinessCredits();
        } else {
            int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
            if (maxDim <= 0) maxDim = 512;
            promptTokens = TokenCostCalculator.imageBusinessPromptTokens(maxDim);
            completionTokens = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
            businessCredits = TokenCostCalculator.usdToCredits(
                    TokenCostCalculator.calculateCost(TranslationContentType.IMAGE, account,
                            promptTokens, completionTokens, 0));
        }
        return SubTaskResult.builder()
                .translatedFile(translatedFile)
                .policyFallbackReason(policyFallbackReason)
                .businessPromptTokens(promptTokens)
                .businessCompletionTokens(completionTokens)
                .businessCredits(businessCredits)
                .cacheHit(true)
                .build();
    }

    private boolean isPersistedPolicyCompletion(TurboFlowBridgeCompleteRequest request) {
        return Boolean.TRUE.equals(request.getPolicyFallback())
                && "INVALID_ARGUMENT".equals(request.getPolicyFallbackStatus())
                && StrUtil.isNotBlank(request.getImageHash())
                && imagePolicyCacheRepository.findByImageHash(request.getImageHash()).isPresent();
    }

    private void saveImagePolicyCache(String imageHash, MultimediaFile sourceFile,
                                      String apiStatus, String reason) {
        if (StrUtil.isBlank(imageHash)) {
            throw new IllegalArgumentException("policy fallback image hash is empty");
        }
        try {
            imagePolicyCacheRepository.save(ImagePolicyCache.builder()
                    .imageHash(imageHash)
                    .sourceFile(sourceFile)
                    .apiStatus(apiStatus)
                    .reason(reason)
                    .build());
        } catch (DataIntegrityViolationException e) {
            if (imagePolicyCacheRepository.findByImageHash(imageHash).isEmpty()) {
                throw e;
            }
            log.debug("[TurboFlowBridge] image policy cache already exists: sourceImageId={}, imageHash={}",
                    sourceFile.getId(), imageHash);
        }
    }

    @Getter
    static class TurboFlowBridgeState {
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

        static TurboFlowBridgeState from(Long aiAccountId, TurboFlowBridgePollRequest request) {
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
    }
}
