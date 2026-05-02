package cn.v7soft.admin.task.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Iterator;
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
import cn.v7soft.dao.entities.primary.AiTranslateUsageRecord;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTranslateUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.ImageTranslationCacheRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

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
@RequiredArgsConstructor
public class TurboFlowBridgeProvider implements TranslateProvider {

    private static final int TURBOFLOW_LEASE_MINUTES = 10;

    private final IAiAccountService aiAccountService;
    private final IMultimediaFileService multimediaFileService;
    private final ILanguageService languageService;
    private final ImageTranslationCacheRepository imageTranslationCacheRepository;
    private final AiTranslateUsageRecordRepository usageRecordRepository;

    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> internalQueues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AiAccountTranslateSubTask> assignments = new ConcurrentHashMap<>();
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
    public int estimateSubTaskCredits(AiAccountTranslateSubTask subTask) {
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            return TokenCostCalculator.estimateCredits(0, TokenCostCalculator.estimateImageTokens(), InvokeMode.STANDARD);
        }
        int textTokens = TokenCostCalculator.estimateTextTokens(subTask.getContent());
        return TokenCostCalculator.estimateCredits(textTokens, 0, InvokeMode.STANDARD);
    }

    /** 被动分发：仅将子任务存入内部队列，等待 TurboFlow 插件 poll 时取走执行 */
    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        internalQueues
                .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                .offer(subTask);
    }

    /**
     * 插件轮询获取任务。
     * 流程：鉴权 → 检查插件状态 → 从队列取子任务 → 读图片 → 查缓存 → 分配 assignment → 返回图片数据
     */
    public TurboFlowBridgeTaskResponse pollTask(String token, TurboFlowBridgePollRequest request) {
        // 1. 通过 Bearer token 鉴权，找到对应的 AiAccount
        AiAccount account = resolveTurboFlowAccount(token);
        String bridgeId = normalizeBridgeId(request.getBridgeId());
        // 记录插件在线状态（仅用于观测）
        bridgeStates.put(bridgeId, TurboFlowBridgeState.from(account.getId(), request));

        // 2. 检查插件状态：flow 未连接或正忙则不分发
        if (!Boolean.TRUE.equals(request.getFlowConnected())) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("flow not connected").build();
        }
        if (Boolean.TRUE.equals(request.getBusy())) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("bridge busy").build();
        }

        // 3. 从该账号的内部队列取一个子任务（FIFO）
        ConcurrentLinkedQueue<AiAccountTranslateSubTask> queue = internalQueues.get(account.getId());
        if (queue == null || queue.isEmpty()) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
        }

        AiAccountTranslateSubTask subTask = queue.poll();
        if (subTask == null) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
        }

        // 非图片任务：TurboFlow 无法处理，返回原文作为译文（避免产物为空）
        if (subTask.getType() != AiAccountTranslateSubTaskType.IMAGE) {
            SubTaskResult.SubTaskResultBuilder resultBuilder = SubTaskResult.builder();
            if (subTask.getType() == AiAccountTranslateSubTaskType.HTML) {
                resultBuilder.translatedHtml(subTask.getContent());
            } else {
                resultBuilder.translatedText(subTask.getContent());
            }
            callback.onSubTaskCompleted(subTask, resultBuilder.build());
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("non-image task skipped").build();
        }

        // 检查父任务是否仍然存活；不活跃时通知失败以释放槽位
        if (!callback.isTaskActive(subTask.getTaskId())) {
            callback.onSubTaskFailed(subTask, "parent task no longer active", false, null);
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("task missing").build();
        }

        try {
            // 4. 读取源图片并计算哈希
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            byte[] imageBytes = readImageBytes(sourceFile);
            String imageHash = DigestUtil.sha256Hex(imageBytes);
            subTask.setImageHash(imageHash);

            // 5. 二次缓存检查：入队后到 poll 之间，其他任务可能已生成同图同语言的缓存
            Optional<ImageTranslationCache> cached = imageTranslationCacheRepository
                    .findByImageHashAndLanguageId(imageHash, Long.parseLong(subTask.getLanguageId()));
            if (cached.isPresent()) {
                MultimediaFile translatedFile = cached.get().isSkipped() ? null : cached.get().getTranslatedFile();
                Language language = resolveLanguage(subTask);

                int promptTokens;
                int completionTokens;
                int businessCredits;
                Optional<AiTranslateUsageRecord> historyOpt = usageRecordRepository
                        .findFirstByContentHashAndTargetLanguageAndCacheHitFalseOrderByCreateTimeDesc(
                                subTask.getContentKey(), language.getName());
                if (historyOpt.isPresent() && historyOpt.get().getBusinessCredits() > 0) {
                    AiTranslateUsageRecord history = historyOpt.get();
                    promptTokens = history.getBusinessPromptTokens();
                    completionTokens = history.getBusinessCompletionTokens();
                    businessCredits = history.getBusinessCredits();
                } else {
                    promptTokens = 718;
                    completionTokens = TokenCostCalculator.estimateImageTokens();
                    businessCredits = TokenCostCalculator.estimateCredits(0, completionTokens, InvokeMode.STANDARD);
                }

                SubTaskResult result = SubTaskResult.builder()
                        .translatedFile(translatedFile)
                        .businessPromptTokens(promptTokens)
                        .businessCompletionTokens(completionTokens)
                        .businessCredits(businessCredits)
                        .cacheHit(true)
                        .build();
                callback.onSubTaskCompleted(subTask, result);
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("cache hit").build();
            }

            // 6. 分配 assignmentId + lease，跟踪该子任务直到 complete/fail/expire
            String assignmentId = UUID.randomUUID().toString();
            LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(TURBOFLOW_LEASE_MINUTES);
            subTask.dispatch(bridgeId, assignmentId, leaseUntil);
            assignments.put(assignmentId, subTask);

            // 7. 构建响应：图片 Base64 + 目标语言 + lease 信息
            Language language = resolveLanguage(subTask);

            return TurboFlowBridgeTaskResponse.builder()
                    .hasTask(true)
                    .taskId(subTask.getTaskId())
                    .subTaskId(subTask.getSubTaskId())
                    .assignmentId(assignmentId)
                    .imageBase64(Base64.getEncoder().encodeToString(imageBytes))
                    .fileName(sourceFile.getName() + "." + sourceFile.getSuffix())
                    .mimeType(toMimeType(sourceFile.getSuffix()))
                    .targetLanguage(language.getName())
                    .targetLanguageCode(language.getCode())
                    .sourceWidth(sourceFile.getWidth())
                    .sourceHeight(sourceFile.getHeight())
                    .leaseUntil(leaseUntil)
                    .build();
        } catch (Exception e) {
            // 分发失败，通知 adapter 进入重试流程
            callback.onSubTaskFailed(subTask, "dispatch failed: " + e.getMessage(), true, null);
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
        AiAccount account = resolveTurboFlowAccount(token);
        AiAccountTranslateSubTask subTask = assignments.get(request.getAssignmentId());
        if (subTask == null) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        // 三重归属校验：token 对应的账号 + bridgeId + assignmentId 都必须匹配
        if (!account.getId().equals(subTask.getAiAccountId())) {
            throw new IllegalArgumentException("assignment does not belong to account");
        }
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            throw new IllegalArgumentException("assignment does not belong to bridge");
        }
        // 原子移除 assignment，防止重复 complete
        if (!assignments.remove(request.getAssignmentId(), subTask)) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        try {
            // 解码并保存翻译后的图片
            byte[] imageBytes = decodeBase64Image(request.getResultImageBase64());
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            String suffix = suffixFromMimeType(request.getResultMimeType(), sourceFile.getSuffix());
            MultimediaFile translatedFile = multimediaFileService.saveTranslatedImage(imageBytes, suffix, subTask.getOwner());

            // 保存翻译缓存，供后续相同图片+语言直接命中
            Language language = resolveLanguage(subTask);
            saveImageTranslationCache(subTask.getImageHash(), sourceFile, language, translatedFile, false);

            // 计算业务 token 用量（按图片分辨率档位）
            int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
            if (maxDim <= 0) maxDim = 512;
            int promptTokens = 718;
            int completionTokens = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);
            int businessCredits = TokenCostCalculator.usdToCredits(
                    TokenCostCalculator.calculateCost(TranslationContentType.IMAGE, InvokeMode.STANDARD,
                            promptTokens, completionTokens, 0));

            // 将翻译结果和 token 用量打包回传给 adapter
            SubTaskResult result = SubTaskResult.builder()
                    .translatedFile(translatedFile)
                    .elapsedMs(request.getElapsedMs())
                    .resultMimeType(request.getResultMimeType())
                    .businessPromptTokens(promptTokens)
                    .businessCompletionTokens(completionTokens)
                    .businessCredits(businessCredits)
                    .build();
            callback.onSubTaskCompleted(subTask, result);
        } catch (Exception e) {
            callback.onSubTaskFailed(subTask, "complete processing failed: " + e.getMessage(), true, null);
            log.error("[TurboFlowBridge] completeTask 处理失败, 已推入重试队列: assignmentId={}",
                    request.getAssignmentId(), e);
            throw new IllegalStateException("complete turboflow task failed: " + e.getMessage(), e);
        }
    }

    /** 插件上报翻译失败。验证归属后通过 callback 通知 adapter（由 adapter 决定重试或标记失败） */
    public void failTask(String token, TurboFlowBridgeFailRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        AiAccountTranslateSubTask subTask = assignments.get(request.getAssignmentId());
        if (subTask == null) {
            return;
        }
        if (!account.getId().equals(subTask.getAiAccountId())) {
            throw new IllegalArgumentException("assignment does not belong to account");
        }
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            throw new IllegalArgumentException("assignment does not belong to bridge");
        }
        if (!assignments.remove(request.getAssignmentId(), subTask)) {
            return;
        }
        String message = StrUtil.blankToDefault(request.getMessage(), "TurboFlow task failed");
        boolean retryable = request.getRetryable() == null || Boolean.TRUE.equals(request.getRetryable());
        callback.onSubTaskFailed(subTask, message, retryable, null);
    }

    /** 回收 lease 过期的 assignment。由 syncTaskStatus 定时器(5s)调用 */
    @Override
    public void reclaimExpiredAssignments() {
        LocalDateTime now = LocalDateTime.now();
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
            callback.onSubTaskExpired(subTask, "TurboFlow lease expired");
        }
    }

    // --- private helpers ---

    /** 通过 Bearer token 查找对应的 TurboFlow AiAccount */
    private AiAccount resolveTurboFlowAccount(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("missing bridge token");
        }
        return aiAccountService.findAvailableAccounts(AiProvider.TURBOFLOW_GEMINI).stream()
                .filter(account -> token.equals(account.getApiKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid TurboFlow bridge token"));
    }

    private String normalizeBridgeId(String bridgeId) {
        return StrUtil.isBlank(bridgeId) ? "unknown" : bridgeId.trim();
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
        } catch (DataIntegrityViolationException e) {
            log.debug("[TurboFlowBridge] image cache already exists: hash={}", imageHash);
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
