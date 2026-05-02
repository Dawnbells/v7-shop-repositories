package cn.v7soft.admin.task.provider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.admin.task.AiAccountTranslateSubTaskType;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
import cn.v7soft.dao.enums.TranslationContentType;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeminiOfficialProvider implements TranslateProvider {

    private final GeminiTranslateService geminiTranslateService;
    private final ILanguageService languageService;
    private final IMultimediaFileService multimediaFileService;
    private final ThreadPoolTaskExecutor executor;
    private final RateLimiter rateLimiter;

    private volatile TranslateProviderCallback callback;

    public GeminiOfficialProvider(
            GeminiTranslateService geminiTranslateService,
            ILanguageService languageService,
            IMultimediaFileService multimediaFileService,
            @Qualifier("translationExecutor") ThreadPoolTaskExecutor executor,
            RateLimiter geminiRateLimiter) {
        this.geminiTranslateService = geminiTranslateService;
        this.languageService = languageService;
        this.multimediaFileService = multimediaFileService;
        this.executor = executor;
        this.rateLimiter = geminiRateLimiter;
    }

    @Override
    public AiProvider getProviderType() {
        return AiProvider.GEMINI_OFFICIAL_STANDARD;
    }

    @Override
    public void setCallback(TranslateProviderCallback callback) {
        this.callback = callback;
    }

    @Override
    public int estimateSubTaskCredits(AiAccountTranslateSubTask subTask) {
        InvokeMode mode = getProviderType().getInvokeMode();
        if (subTask.getType() == AiAccountTranslateSubTaskType.IMAGE) {
            return TokenCostCalculator.estimateCredits(0, TokenCostCalculator.estimateImageTokens(), mode);
        }
        int textTokens = TokenCostCalculator.estimateTextTokens(subTask.getContent());
        return TokenCostCalculator.estimateCredits(textTokens, 0, mode);
    }

    @Override
    public void executeSubTask(AiAccountTranslateSubTask subTask) {
        subTask.start();
        subTask.getAttemptCount().incrementAndGet();
        executor.submit(() -> doExecute(subTask));
    }

    private void doExecute(AiAccountTranslateSubTask subTask) {
        try {
            RateLimiter.waitForPermission(rateLimiter);

            Language language = languageService.getById(Long.parseLong(subTask.getLanguageId()));
            String langName = language.getName();

            switch (subTask.getType()) {
                case TEXT -> executeText(subTask, langName);
                case HTML -> executeHtml(subTask, langName);
                case IMAGE -> executeImage(subTask, langName);
            }
        } catch (Exception e) {
            log.error("[GeminiOfficialProvider] subtask failed: subTaskId={}", subTask.getSubTaskId(), e);
            boolean retryable = cn.v7soft.admin.configurer.TranslationExecutorConfig.isRetryable(e);
            callback.onSubTaskFailed(subTask, e.getMessage(), retryable, null);
        }
    }

    private void executeText(AiAccountTranslateSubTask subTask, String langName) {
        AtomicReference<GeminiTranslateService.TokenUsage> usageRef = new AtomicReference<>();
        String translated = geminiTranslateService.translateTextRaw(subTask.getContent(), langName, usageRef::set);

        GeminiTranslateService.TokenUsage usage = usageRef.get();
        int prompt = usage != null ? safeInt(usage.getPromptTokens()) : 0;
        int completion = usage != null ? safeInt(usage.getCompletionTokens()) : 0;
        int thinking = usage != null ? safeInt(usage.getThinkingTokens()) : 0;

        BigDecimal cost = TokenCostCalculator.calculateCost(
                TranslationContentType.TEXT, InvokeMode.STANDARD, prompt, completion, thinking);

        SubTaskResult result = SubTaskResult.builder()
                .translatedText(translated)
                .elapsedMs(usage != null ? usage.getElapsedMs() : null)
                .actualPromptTokens(prompt)
                .actualCompletionTokens(completion)
                .actualThinkingTokens(thinking)
                .businessPromptTokens(prompt)
                .businessCompletionTokens(completion)
                .businessThinkingTokens(thinking)
                .businessCredits(TokenCostCalculator.usdToCredits(cost))
                .build();
        callback.onSubTaskCompleted(subTask, result);
    }

    private void executeHtml(AiAccountTranslateSubTask subTask, String langName) {
        AtomicReference<GeminiTranslateService.TokenUsage> usageRef = new AtomicReference<>();
        String translated = geminiTranslateService.translateHtmlRaw(subTask.getContent(), langName, usageRef::set);

        GeminiTranslateService.TokenUsage usage = usageRef.get();
        int prompt = usage != null ? safeInt(usage.getPromptTokens()) : 0;
        int completion = usage != null ? safeInt(usage.getCompletionTokens()) : 0;
        int thinking = usage != null ? safeInt(usage.getThinkingTokens()) : 0;

        BigDecimal cost = TokenCostCalculator.calculateCost(
                TranslationContentType.HTML, InvokeMode.STANDARD, prompt, completion, thinking);

        SubTaskResult result = SubTaskResult.builder()
                .translatedHtml(translated)
                .elapsedMs(usage != null ? usage.getElapsedMs() : null)
                .actualPromptTokens(prompt)
                .actualCompletionTokens(completion)
                .actualThinkingTokens(thinking)
                .businessPromptTokens(prompt)
                .businessCompletionTokens(completion)
                .businessThinkingTokens(thinking)
                .businessCredits(TokenCostCalculator.usdToCredits(cost))
                .build();
        callback.onSubTaskCompleted(subTask, result);
    }

    private void executeImage(AiAccountTranslateSubTask subTask, String langName) throws Exception {
        MultimediaFile sourceFile = subTask.resolveSourceFile(multimediaFileService);
        byte[] imageBytes = readImageBytes(sourceFile);
        String mimeType = toMimeType(sourceFile.getSuffix());

        AtomicReference<GeminiTranslateService.TokenUsage> usageRef = new AtomicReference<>();
        byte[] resultBytes = geminiTranslateService.translateImageRaw(imageBytes, mimeType, langName, usageRef::set);

        GeminiTranslateService.TokenUsage usage = usageRef.get();
        int actualPrompt = usage != null ? safeInt(usage.getPromptTokens()) : 0;
        int actualCompletion = usage != null ? safeInt(usage.getCompletionTokens()) : 0;
        int actualThinking = usage != null ? safeInt(usage.getThinkingTokens()) : 0;

        int maxDim = Math.max(sourceFile.getWidth(), sourceFile.getHeight());
        if (maxDim <= 0) maxDim = 512;
        int bizPrompt = 718;
        int bizCompletion = TokenCostCalculator.imageBusinessCompletionTokens(maxDim);

        MultimediaFile translatedFile = null;
        if (resultBytes != null) {
            translatedFile = multimediaFileService.saveTranslatedImage(
                    resultBytes, sourceFile.getSuffix(), subTask.getOwner());
        }

        BigDecimal cost = TokenCostCalculator.calculateCost(
                TranslationContentType.IMAGE, InvokeMode.STANDARD, bizPrompt, bizCompletion, 0);

        SubTaskResult result = SubTaskResult.builder()
                .translatedFile(translatedFile)
                .elapsedMs(usage != null ? usage.getElapsedMs() : null)
                .actualPromptTokens(actualPrompt)
                .actualCompletionTokens(actualCompletion)
                .actualThinkingTokens(actualThinking)
                .businessPromptTokens(bizPrompt)
                .businessCompletionTokens(bizCompletion)
                .businessThinkingTokens(0)
                .businessCredits(TokenCostCalculator.usdToCredits(cost))
                .build();
        callback.onSubTaskCompleted(subTask, result);
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
}
