package cn.v7soft.admin.task.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobDestination;

import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskType;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.TranslationContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gemini Batch API Provider。
 * <p>
 * 收集子任务 → 构建 JSONL → 上传并创建 BatchJob → 轮询完成 → 解析结果回调。
 * <p>
 * reclaimExpiredAssignments（每 5s 调用一次）负责刷批、轮询、处理结果和超时。
 */
@Slf4j
@Component
public class GeminiOfficialBatchProvider implements TranslateProvider {

    private static final int FLUSH_THRESHOLD = 50;
    private static final long FLUSH_TIMEOUT_MS = 30_000;
    private static final long BATCH_JOB_TIMEOUT_MS = 60 * 60_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> COMPLETED_STATES = Set.of(
            "JOB_STATE_SUCCEEDED", "JOB_STATE_FAILED", "JOB_STATE_CANCELLED",
            "JOB_STATE_EXPIRED", "JOB_STATE_PARTIALLY_SUCCEEDED");
    private static final Set<String> DOWNLOADABLE_STATES = Set.of(
            "JOB_STATE_SUCCEEDED", "JOB_STATE_PARTIALLY_SUCCEEDED");

    private final GeminiTranslateService geminiTranslateService;
    private final IAiAccountService aiAccountService;
    private final ILanguageService languageService;
    private final IMultimediaFileService multimediaFileService;

    private volatile TranslateProviderCallback callback;

    private final ConcurrentLinkedQueue<BatchEntry> pendingQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, ActiveBatch> activeBatches = new ConcurrentHashMap<>();
    private volatile long lastFlushTime = System.currentTimeMillis();

    public GeminiOfficialBatchProvider(
            GeminiTranslateService geminiTranslateService,
            IAiAccountService aiAccountService,
            ILanguageService languageService,
            IMultimediaFileService multimediaFileService) {
        this.geminiTranslateService = geminiTranslateService;
        this.aiAccountService = aiAccountService;
        this.languageService = languageService;
        this.multimediaFileService = multimediaFileService;
    }

    @Override
    public AiProvider getProviderType() {
        return AiProvider.GEMINI_OFFICIAL_BATCH;
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
        subTask.start();
        subTask.getAttemptCount().incrementAndGet();
        try {
            log.debug("[GeminiBatch] preparing batch entry: taskId={}, subTaskId={}, type={}, attempt={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                    subTask.getAttemptCount().get());
            BatchEntry entry = prepareEntry(subTask);
            pendingQueue.offer(entry);
            log.debug("[GeminiBatch] queued subTask: taskId={}, subTaskId={}, type={}, pendingSize={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(), pendingQueue.size());
        } catch (Exception e) {
            log.error("[GeminiBatch] failed to prepare batch entry: {}", subTask.getSubTaskId(), e);
            callback.onSubTaskFailed(subTask, "prepare failed: " + e.getMessage(), true, null);
        }
    }

    @Override
    public void reclaimExpiredAssignments() {
        tryFlushPending();
        pollActiveBatches();
    }

    @Override
    public void onTaskCancelling(Long taskId) {
        // 1. 从 pendingQueue 移除：尚未提交到 Gemini，不计费
        Iterator<BatchEntry> pendingIt = pendingQueue.iterator();
        while (pendingIt.hasNext()) {
            BatchEntry entry = pendingIt.next();
            if (taskId.equals(entry.subTask.getTaskId())) {
                pendingIt.remove();
                callback.onSubTaskFailed(entry.subTask, "task cancelled", false, null);
            }
        }
        // 2. activeBatches 中的 entries：已提交到 Gemini，可能已消耗 token，按预估计费
        //    必须从 entries map 中移除，否则 batch 完成时 processBatchResult 会再次回调导致双重 releaseSlot
        for (ActiveBatch ab : activeBatches.values()) {
            Iterator<Map.Entry<String, BatchEntry>> entryIt = ab.entries.entrySet().iterator();
            while (entryIt.hasNext()) {
                BatchEntry entry = entryIt.next().getValue();
                if (taskId.equals(entry.subTask.getTaskId())) {
                    entryIt.remove();
                    callback.onSubTaskFailed(entry.subTask, "task cancelled (batch in-flight)",
                            false, buildEstimatedResult(entry));
                }
            }
        }
    }

    // ======================== 准备 Entry ========================

    private BatchEntry prepareEntry(AiAccountTranslateSubTask subTask) throws Exception {
        Language language = languageService.getById(Long.parseLong(subTask.getLanguageId()));
        BatchEntry entry = new BatchEntry();
        entry.subTask = subTask;
        entry.languageName = language.getName();
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
            entry.imageBytes = readImageBytes(sourceFile);
            entry.imageMimeType = toMimeType(sourceFile.getSuffix());
            entry.imageMaxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
            if (entry.imageMaxDim <= 0) entry.imageMaxDim = 512;
            entry.sourceFile = sourceFile;
            log.debug("[GeminiBatch] image batch entry prepared: taskId={}, subTaskId={}, imageId={}, mimeType={}, bytes={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), sourceFile.getId(),
                    entry.imageMimeType, entry.imageBytes.length);
        } else {
            log.debug("[GeminiBatch] text/html batch entry prepared: taskId={}, subTaskId={}, type={}, contentLength={}",
                    subTask.getTaskId(), subTask.getSubTaskId(), subTask.getType(),
                    subTask.getContent() == null ? 0 : subTask.getContent().length());
        }
        return entry;
    }

    // ======================== Flush ========================

    private void tryFlushPending() {
        int size = pendingQueue.size();
        if (size == 0) return;

        boolean shouldFlush = size >= FLUSH_THRESHOLD
                || (System.currentTimeMillis() - lastFlushTime) > FLUSH_TIMEOUT_MS;
        if (!shouldFlush) return;
        log.debug("[GeminiBatch] flush triggered: pendingSize={}, threshold={}, timeoutMs={}",
                size, FLUSH_THRESHOLD, FLUSH_TIMEOUT_MS);

        List<BatchEntry> batch = new ArrayList<>();
        while (!pendingQueue.isEmpty() && batch.size() < FLUSH_THRESHOLD * 2) {
            BatchEntry e = pendingQueue.poll();
            if (e != null) batch.add(e);
        }
        if (batch.isEmpty()) return;
        lastFlushTime = System.currentTimeMillis();

        try {
            submitBatch(batch);
        } catch (Exception e) {
            log.error("[GeminiBatch] flush failed, failing {} subtasks", batch.size(), e);
            for (BatchEntry entry : batch) {
                callback.onSubTaskFailed(entry.subTask, "batch submit failed: " + e.getMessage(), true, null);
            }
            // submit 失败说明 Gemini 尚未处理，不计费
        }
    }

    private void submitBatch(List<BatchEntry> entries) throws Exception {
        StringBuilder jsonl = new StringBuilder();
        Map<String, BatchEntry> keyMap = new LinkedHashMap<>();

        for (BatchEntry entry : entries) {
            String key = entry.subTask.getSubTaskId();
            String line = buildJsonlEntry(key, entry);
            jsonl.append(line).append('\n');
            keyMap.put(key, entry);
        }
        log.debug("[GeminiBatch] jsonl built: entries={}, bytes={}", entries.size(), jsonl.length());

        String uploadedFileName = geminiTranslateService.uploadBatchFile(jsonl.toString());
        log.debug("[GeminiBatch] batch file uploaded: uploadedFileName={}, entries={}", uploadedFileName, entries.size());
        BatchJob job = geminiTranslateService.createBatchJob(uploadedFileName);
        String jobName = job.name().orElseThrow(() -> new RuntimeException("Batch Job 无 name"));

        ActiveBatch ab = new ActiveBatch();
        ab.jobName = jobName;
        ab.uploadedFileName = uploadedFileName;
        ab.entries = keyMap;
        ab.createdAt = System.currentTimeMillis();
        activeBatches.put(jobName, ab);

        log.info("[GeminiBatch] submitted batch: jobName={}, entries={}", jobName, entries.size());
    }

    private String buildJsonlEntry(String key, BatchEntry entry) {
        AiAccountTranslateSubTask subTask = entry.subTask;
        return switch (subTask.getType()) {
            case TEXT -> geminiTranslateService.buildTextTranslateJsonlEntry(key, subTask.getContent(), entry.languageName);
            case HTML -> geminiTranslateService.buildHtmlTranslateJsonlEntry(key, subTask.getContent(), entry.languageName);
            case IMAGE -> geminiTranslateService.buildImageTranslateJsonlEntry(key, entry.imageBytes, entry.imageMimeType, entry.languageName);
        };
    }

    // ======================== Poll & Process ========================

    private void pollActiveBatches() {
        Iterator<Map.Entry<String, ActiveBatch>> it = activeBatches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveBatch> mapEntry = it.next();
            ActiveBatch ab = mapEntry.getValue();
            try {
                BatchJob job = geminiTranslateService.getBatchJob(ab.jobName);
                String state = job.state().map(Object::toString).orElse("UNKNOWN");
                log.debug("[GeminiBatch] batch polled: jobName={}, state={}, entries={}",
                        ab.jobName, state, ab.entries.size());

                if (COMPLETED_STATES.contains(state)) {
                    it.remove();
                    processBatchResult(ab, job, state);
                    cleanupBatchFiles(ab, job);
                } else if (System.currentTimeMillis() - ab.createdAt > BATCH_JOB_TIMEOUT_MS) {
                    it.remove();
                    log.warn("[GeminiBatch] batch timed out: jobName={}", ab.jobName);
                    geminiTranslateService.cancelBatchJob(ab.jobName);
                    failAllEntries(ab, "batch job timed out", true);
                    cleanupBatchFiles(ab, null);
                }
            } catch (Exception e) {
                log.warn("[GeminiBatch] poll failed for jobName={}: {}", ab.jobName, e.getMessage());
            }
        }
    }

    private void processBatchResult(ActiveBatch ab, BatchJob job, String state) {
        if (!DOWNLOADABLE_STATES.contains(state)) {
            log.warn("[GeminiBatch] batch not downloadable: jobName={}, state={}", ab.jobName, state);
            failAllEntries(ab, "batch ended with state: " + state, true);
            return;
        }

        String resultFileName = job.dest().flatMap(BatchJobDestination::fileName).orElse(null);
        if (resultFileName == null) {
            log.warn("[GeminiBatch] no result file: jobName={}", ab.jobName);
            failAllEntries(ab, "no result file in batch response", true);
            return;
        }

        Map<String, JsonNode> resultMap;
        try {
            String content = geminiTranslateService.downloadBatchResult(resultFileName);
            resultMap = parseResultJsonl(content);
            log.debug("[GeminiBatch] batch result downloaded: jobName={}, resultFile={}, resultCount={}",
                    ab.jobName, resultFileName, resultMap.size());
        } catch (Exception e) {
            log.error("[GeminiBatch] download result failed: jobName={}", ab.jobName, e);
            failAllEntries(ab, "download result failed: " + e.getMessage(), true);
            return;
        }

        for (Map.Entry<String, BatchEntry> e : ab.entries.entrySet()) {
            String key = e.getKey();
            BatchEntry entry = e.getValue();
            JsonNode node = resultMap.get(key);
            if (node == null || !node.has("response")) {
                log.debug("[GeminiBatch] batch result missing entry: jobName={}, key={}", ab.jobName, key);
                callback.onSubTaskFailed(entry.subTask, "no result in batch for key: " + key,
                        false, buildEstimatedResult(entry));
                continue;
            }
            try {
                log.debug("[GeminiBatch] processing batch result entry: jobName={}, key={}, type={}",
                        ab.jobName, key, entry.subTask.getType());
                processEntryResult(entry, node.get("response"));
            } catch (Exception ex) {
                log.error("[GeminiBatch] process entry failed: key={}", key, ex);
                callback.onSubTaskFailed(entry.subTask, "process failed: " + ex.getMessage(),
                        false, buildEstimatedResult(entry));
            }
        }
    }

    private void processEntryResult(BatchEntry entry, JsonNode responseNode) throws Exception {
        AiAccountTranslateSubTask subTask = entry.subTask;
        AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
        GeminiTranslateService.TokenUsage usage = GeminiTranslateService.extractTokenUsageFromBatchResponse(responseNode);
        int actualPrompt = usage != null ? safeInt(usage.getPromptTokens()) : 0;
        int actualCompletion = usage != null ? safeInt(usage.getCompletionTokens()) : 0;
        int actualThinking = usage != null ? safeInt(usage.getThinkingTokens()) : 0;

        switch (subTask.getType()) {
            case TEXT -> {
                String translated = extractText(responseNode);
                log.debug("[GeminiBatch] text result parsed: taskId={}, subTaskId={}, actualTokens={}",
                        subTask.getTaskId(), subTask.getSubTaskId(),
                        actualPrompt + actualCompletion + actualThinking);
                BigDecimal cost = TokenCostCalculator.calculateCost(
                        TranslationContentType.TEXT, account, actualPrompt, actualCompletion, actualThinking);
                SubTaskResult result = SubTaskResult.builder()
                        .translatedText(translated)
                        .actualPromptTokens(actualPrompt)
                        .actualCompletionTokens(actualCompletion)
                        .actualThinkingTokens(actualThinking)
                        .businessPromptTokens(actualPrompt)
                        .businessCompletionTokens(actualCompletion)
                        .businessThinkingTokens(actualThinking)
                        .businessCredits(TokenCostCalculator.usdToCredits(cost))
                        .build();
                callback.onSubTaskCompleted(subTask, result);
            }
            case HTML -> {
                String translated = extractText(responseNode);
                log.debug("[GeminiBatch] html result parsed: taskId={}, subTaskId={}, actualTokens={}",
                        subTask.getTaskId(), subTask.getSubTaskId(),
                        actualPrompt + actualCompletion + actualThinking);
                BigDecimal cost = TokenCostCalculator.calculateCost(
                        TranslationContentType.HTML, account, actualPrompt, actualCompletion, actualThinking);
                SubTaskResult result = SubTaskResult.builder()
                        .translatedHtml(translated)
                        .actualPromptTokens(actualPrompt)
                        .actualCompletionTokens(actualCompletion)
                        .actualThinkingTokens(actualThinking)
                        .businessPromptTokens(actualPrompt)
                        .businessCompletionTokens(actualCompletion)
                        .businessThinkingTokens(actualThinking)
                        .businessCredits(TokenCostCalculator.usdToCredits(cost))
                        .build();
                callback.onSubTaskCompleted(subTask, result);
            }
            case IMAGE -> {
                byte[] imgBytes = extractImage(responseNode);
                log.debug("[GeminiBatch] image result parsed: taskId={}, subTaskId={}, hasImage={}, actualTokens={}",
                        subTask.getTaskId(), subTask.getSubTaskId(), imgBytes != null,
                        actualPrompt + actualCompletion + actualThinking);
                int bizPrompt = 718;
                int bizCompletion = TokenCostCalculator.imageBusinessCompletionTokens(entry.imageMaxDim);
                BigDecimal cost = TokenCostCalculator.calculateCost(
                        TranslationContentType.IMAGE, account, bizPrompt, bizCompletion, 0);
                try {
                    MultimediaFile translatedFile = null;
                    if (imgBytes != null && entry.sourceFile != null) {
                        translatedFile = multimediaFileService.saveTranslatedImage(
                                imgBytes, entry.sourceFile.getSuffix(), subTask.getOwner());
                    }
                    log.debug("[GeminiBatch] image result saved: taskId={}, subTaskId={}, translatedFileId={}, skipped={}",
                            subTask.getTaskId(), subTask.getSubTaskId(),
                            translatedFile == null ? null : translatedFile.getId(), translatedFile == null);
                    SubTaskResult result = SubTaskResult.builder()
                            .translatedFile(translatedFile)
                            .actualPromptTokens(actualPrompt)
                            .actualCompletionTokens(actualCompletion)
                            .actualThinkingTokens(actualThinking)
                            .businessPromptTokens(bizPrompt)
                            .businessCompletionTokens(bizCompletion)
                            .businessThinkingTokens(0)
                            .businessCredits(TokenCostCalculator.usdToCredits(cost))
                            .build();
                    callback.onSubTaskCompleted(subTask, result);
                } catch (Exception e) {
                    log.error("[GeminiBatch] image save failed: subTaskId={}", subTask.getSubTaskId(), e);
                    SubTaskResult partial = SubTaskResult.builder()
                            .actualPromptTokens(actualPrompt)
                            .actualCompletionTokens(actualCompletion)
                            .actualThinkingTokens(actualThinking)
                            .businessPromptTokens(bizPrompt)
                            .businessCompletionTokens(bizCompletion)
                            .businessThinkingTokens(0)
                            .businessCredits(TokenCostCalculator.usdToCredits(cost))
                            .build();
                    callback.onSubTaskFailed(subTask, "image save failed: " + e.getMessage(), false, partial);
                }
            }
        }
    }

    // ======================== 结果解析 ========================

    private Map<String, JsonNode> parseResultJsonl(String content) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            try {
                JsonNode node = MAPPER.readTree(line);
                String key = node.has("key") ? node.get("key").asText() : null;
                if (key != null) map.put(key, node);
            } catch (Exception e) {
                log.warn("[GeminiBatch] parse result line failed: {}", e.getMessage());
            }
        }
        return map;
    }

    private static String extractText(JsonNode responseNode) {
        JsonNode candidates = responseNode.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return null;
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return null;
        for (JsonNode part : parts) {
            if (part.has("text")) return part.get("text").asText();
        }
        return null;
    }

    private static byte[] extractImage(JsonNode responseNode) {
        JsonNode candidates = responseNode.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return null;
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) return null;
        for (JsonNode part : parts) {
            JsonNode inlineData = part.path("inlineData");
            if (!inlineData.isMissingNode() && inlineData.has("data")) {
                return Base64.getDecoder().decode(inlineData.get("data").asText());
            }
            JsonNode inline = part.path("inline_data");
            if (!inline.isMissingNode() && inline.has("data")) {
                return Base64.getDecoder().decode(inline.get("data").asText());
            }
        }
        return null;
    }

    // ======================== 辅助 ========================

    private void failAllEntries(ActiveBatch ab, String message, boolean billable) {
        for (BatchEntry entry : ab.entries.values()) {
            SubTaskResult partial = billable ? buildEstimatedResult(entry) : null;
            callback.onSubTaskFailed(entry.subTask, message, !billable, partial);
        }
    }

    private SubTaskResult buildEstimatedResult(BatchEntry entry) {
        AiAccountTranslateSubTask subTask = entry.subTask;
        TranslationContentType ct = mapContentType(subTask.getType());
        int bizPrompt, bizCompletion;
        if (ct == TranslationContentType.IMAGE) {
            bizPrompt = 718;
            bizCompletion = TokenCostCalculator.imageBusinessCompletionTokens(
                    entry.imageMaxDim > 0 ? entry.imageMaxDim : 512);
        } else {
            int est = TokenCostCalculator.estimateTextTokens(subTask.getContent());
            bizPrompt = est;
            bizCompletion = est;
        }
        AiAccount account = aiAccountService.getById(subTask.getAiAccountId());
        BigDecimal cost = TokenCostCalculator.calculateCost(ct, account, bizPrompt, bizCompletion, 0);
        return SubTaskResult.builder()
                .businessPromptTokens(bizPrompt)
                .businessCompletionTokens(bizCompletion)
                .businessThinkingTokens(0)
                .businessCredits(TokenCostCalculator.usdToCredits(cost))
                .build();
    }

    private static TranslationContentType mapContentType(AiAccountTranslateSubTaskType type) {
        return switch (type) {
            case TEXT -> TranslationContentType.TEXT;
            case HTML -> TranslationContentType.HTML;
            case IMAGE -> TranslationContentType.IMAGE;
        };
    }

    private void cleanupBatchFiles(ActiveBatch ab, BatchJob job) {
        try {
            if (ab.uploadedFileName != null) {
                geminiTranslateService.deleteFile(ab.uploadedFileName);
            }
            if (job != null) {
                String resultFile = job.dest().flatMap(BatchJobDestination::fileName).orElse(null);
                if (resultFile != null) {
                    geminiTranslateService.deleteFile(resultFile);
                }
                geminiTranslateService.deleteBatchJob(ab.jobName);
            }
        } catch (Exception e) {
            log.warn("[GeminiBatch] cleanup failed for jobName={}: {}", ab.jobName, e.getMessage());
        }
    }

    private byte[] readImageBytes(MultimediaFile file) throws Exception {
        try (InputStream in = multimediaFileService.download(String.valueOf(file.getId()), 0);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static String toMimeType(String suffix) {
        if (suffix == null || suffix.isBlank()) return "image/png";
        return switch (suffix.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    // ======================== 内部数据结构 ========================

    private static class BatchEntry {
        AiAccountTranslateSubTask subTask;
        String languageName;
        byte[] imageBytes;
        String imageMimeType;
        int imageMaxDim;
        MultimediaFile sourceFile;
    }

    private static class ActiveBatch {
        String jobName;
        String uploadedFileName;
        Map<String, BatchEntry> entries;
        long createdAt;
    }
}
