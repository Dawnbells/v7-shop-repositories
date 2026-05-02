package cn.v7soft.admin.task.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
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
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.ImageTranslationCache;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
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
 * 完整流程：
 * 1. AiAccountTranslateTask 定时器调用 executeSubTask → 子任务入内部队列
 * 2. TurboFlow 插件调用 pollTask → 从队列取任务，读取图片，分配 assignmentId + lease
 * 3. 插件完成翻译后调用 completeTask → 保存翻译文件和缓存，通过 callback 通知完成
 * 4. 插件失败时调用 failTask → 通过 callback 通知失败（adapter 决定重试策略）
 * 5. syncTaskStatus 定时器调用 reclaimExpiredAssignments → 回收超时的 lease
 * <p>
 * TurboFlowBridgeController 直接注入此 Provider，不再依赖 AiAccountTranslateTask。
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
    private final AiTokenUsageRecordRepository aiTokenUsageRecordRepository;

    // 按账号隔离的内部待 poll 队列，executeSubTask 入队，pollTask 出队
    private final ConcurrentMap<Long, ConcurrentLinkedQueue<AiAccountTranslateSubTask>> internalQueues = new ConcurrentHashMap<>();
    // assignmentId -> 子任务，跟踪已分发给插件但尚未完成的任务（用于 complete/fail/expire 校验）
    private final ConcurrentMap<String, AiAccountTranslateSubTask> assignments = new ConcurrentHashMap<>();
    // bridgeId -> 插件在线状态快照，仅用于观测
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

    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        internalQueues
                .computeIfAbsent(subTask.getAiAccountId(), k -> new ConcurrentLinkedQueue<>())
                .offer(subTask);
    }

    public TurboFlowBridgeTaskResponse pollTask(String token, TurboFlowBridgePollRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        String bridgeId = normalizeBridgeId(request.getBridgeId());
        bridgeStates.put(bridgeId, TurboFlowBridgeState.from(account.getId(), request));

        if (!Boolean.TRUE.equals(request.getFlowConnected())) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("flow not connected").build();
        }
        if (Boolean.TRUE.equals(request.getBusy())) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("bridge busy").build();
        }

        ConcurrentLinkedQueue<AiAccountTranslateSubTask> queue = internalQueues.get(account.getId());
        if (queue == null || queue.isEmpty()) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
        }

        AiAccountTranslateSubTask subTask = queue.poll();
        if (subTask == null) {
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("no task").build();
        }

        if (subTask.getType() != AiAccountTranslateSubTaskType.IMAGE) {
            callback.onSubTaskCompleted(subTask, SubTaskResult.builder().build());
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("non-image task skipped").build();
        }

        if (!callback.isTaskActive(subTask.getTaskId())) {
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
                MultimediaFile translatedFile = cached.get().isSkipped() ? null : cached.get().getTranslatedFile();
                SubTaskResult result = SubTaskResult.builder().translatedFile(translatedFile).build();
                callback.onSubTaskCompleted(subTask, result);
                saveCacheHitTokenRecord(subTask, sourceFile, translatedFile);
                return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("cache hit").build();
            }

            String assignmentId = UUID.randomUUID().toString();
            LocalDateTime leaseUntil = LocalDateTime.now().plusMinutes(TURBOFLOW_LEASE_MINUTES);
            subTask.dispatch(bridgeId, assignmentId, leaseUntil);
            assignments.put(assignmentId, subTask);

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
            callback.onSubTaskFailed(subTask, "dispatch failed: " + e.getMessage(), true);
            log.error("[TurboFlowBridge] 分发任务失败: taskId={}, subTaskId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), e);
            return TurboFlowBridgeTaskResponse.builder().hasTask(false).message("dispatch failed: " + e.getMessage()).build();
        }
    }

    public void completeTask(String token, TurboFlowBridgeCompleteRequest request) {
        AiAccount account = resolveTurboFlowAccount(token);
        AiAccountTranslateSubTask subTask = assignments.get(request.getAssignmentId());
        if (subTask == null) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        if (!account.getId().equals(subTask.getAiAccountId())) {
            throw new IllegalArgumentException("assignment does not belong to account");
        }
        if (!subTask.isAssignedTo(request.getBridgeId(), request.getAssignmentId())) {
            throw new IllegalArgumentException("assignment does not belong to bridge");
        }
        if (!assignments.remove(request.getAssignmentId(), subTask)) {
            throw new IllegalArgumentException("assignment not found or expired");
        }
        try {
            byte[] imageBytes = decodeBase64Image(request.getResultImageBase64());
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            String suffix = suffixFromMimeType(request.getResultMimeType(), sourceFile.getSuffix());
            MultimediaFile translatedFile = multimediaFileService.saveTranslatedImage(imageBytes, suffix, subTask.getOwner());

            Language language = resolveLanguage(subTask);
            saveImageTranslationCache(subTask.getImageHash(), sourceFile, language, translatedFile, false);
            saveImageTokenRecord(subTask, sourceFile, translatedFile, false, request.getElapsedMs());

            SubTaskResult result = SubTaskResult.builder()
                    .translatedFile(translatedFile)
                    .elapsedMs(request.getElapsedMs())
                    .resultMimeType(request.getResultMimeType())
                    .build();
            callback.onSubTaskCompleted(subTask, result);
        } catch (Exception e) {
            assignments.remove(request.getAssignmentId());
            throw new IllegalStateException("complete turboflow task failed: " + e.getMessage(), e);
        }
    }

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
        callback.onSubTaskFailed(subTask, message, retryable);
    }

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
            it.remove();
            log.warn("[TurboFlowBridge] lease expired: taskId={}, subTaskId={}, assignmentId={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), entry.getKey());
            callback.onSubTaskExpired(subTask, "TurboFlow lease expired");
        }
    }

    // --- private helpers ---

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

    private void saveCacheHitTokenRecord(AiAccountTranslateSubTask subTask,
                                         MultimediaFile sourceFile, MultimediaFile translatedFile) {
        int promptTokens = 718;
        int completionTokens = TokenCostCalculator.estimateImageTokens();
        saveTokenUsageRecord(subTask, TranslationContentType.IMAGE, true, null, null,
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
            Language language = resolveLanguage(subTask);
            if (aiTokenUsageRecordRepository.existsByTaskIdAndContentHashAndTargetLanguage(
                    subTask.getTaskId(), subTask.getContentKey(), language.getName())) {
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
                    .targetLanguage(language.getName())
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
