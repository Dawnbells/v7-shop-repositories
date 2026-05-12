package cn.v7soft.admin.service.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.AiTranslateHtmlRequest;
import cn.v7soft.admin.controller.req.AiTranslateImageRequest;
import cn.v7soft.admin.controller.req.AiTranslateTextRequest;
import cn.v7soft.admin.controller.resp.AiTranslateImageResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.IAiTranslateService;
import cn.v7soft.admin.service.IAsyncTaskService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.admin.utils.TokenCostCalculator;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.TaskState;
import cn.v7soft.dao.enums.TaskType;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiTranslateService implements IAiTranslateService {

    private static final Pattern TRAILING_ID_PATTERN = Pattern.compile("/(\\d+)(?:\\?[^/]*)?$");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final GeminiTranslateService geminiTranslateService;
    private final IAiAccountService aiAccountService;
    private final ILanguageService languageService;
    private final IMultimediaFileService multimediaFileService;
    private final AiTokenUsageRecordRepository usageRecordRepository;
    private final ThreadPoolTaskExecutor translationExecutor;
    private final AsyncTaskRepository asyncTaskRepository;
    private final IAsyncTaskService asyncTaskService;

    public AiTranslateService(GeminiTranslateService geminiTranslateService,
                              IAiAccountService aiAccountService,
                              ILanguageService languageService,
                              IMultimediaFileService multimediaFileService,
                              AiTokenUsageRecordRepository usageRecordRepository,
                              @Qualifier("translationExecutor") ThreadPoolTaskExecutor translationExecutor,
                              AsyncTaskRepository asyncTaskRepository,
                              IAsyncTaskService asyncTaskService) {
        this.geminiTranslateService = geminiTranslateService;
        this.aiAccountService = aiAccountService;
        this.languageService = languageService;
        this.multimediaFileService = multimediaFileService;
        this.usageRecordRepository = usageRecordRepository;
        this.translationExecutor = translationExecutor;
        this.asyncTaskRepository = asyncTaskRepository;
        this.asyncTaskService = asyncTaskService;
    }

    @Override
    public void streamText(AiTranslateTextRequest request, SystemUser owner, SseEmitter emitter) {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();
        String preview = truncate(request.getText(), 30);
        AsyncTask task = createRealtimeTask(TranslationContentType.TEXT, targetLang, preview,
                request.getAiAccountId(), owner);

        translationExecutor.submit(() -> {
            try {
                geminiTranslateService.streamTranslateText(
                        account, request.getPrompt(), request.getText(), targetLang,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                log.warn("[streamText] emitter.send failed", e);
                            }
                        },
                        usage -> recordUsage(task.getId(), account, TranslationContentType.TEXT, targetLang, usage, owner),
                        () -> {
                            markCompletedAndSettle(task);
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("[streamText] emitter.complete failed", e);
                            }
                        },
                        error -> {
                            markFailedAndSettle(task, error.getMessage());
                            try {
                                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                                emitter.completeWithError(error);
                            } catch (Exception e) {
                                log.warn("[streamText] emitter.completeWithError failed", e);
                            }
                        }
                );
            } catch (Exception e) {
                log.error("[streamText] unexpected error", e);
                markFailedAndSettle(task, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void streamHtml(AiTranslateHtmlRequest request, SystemUser owner, SseEmitter emitter) {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();
        String preview = truncate(request.getHtml(), 30);
        AsyncTask task = createRealtimeTask(TranslationContentType.HTML, targetLang, preview,
                request.getAiAccountId(), owner);

        translationExecutor.submit(() -> {
            try {
                geminiTranslateService.streamTranslateHtml(
                        account, request.getPrompt(), request.getHtml(), targetLang,
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                log.warn("[streamHtml] emitter.send failed", e);
                            }
                        },
                        usage -> recordUsage(task.getId(), account, TranslationContentType.HTML, targetLang, usage, owner),
                        () -> {
                            markCompletedAndSettle(task);
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("[streamHtml] emitter.complete failed", e);
                            }
                        },
                        error -> {
                            markFailedAndSettle(task, error.getMessage());
                            try {
                                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                                emitter.completeWithError(error);
                            } catch (Exception e) {
                                log.warn("[streamHtml] emitter.completeWithError failed", e);
                            }
                        }
                );
            } catch (Exception e) {
                log.error("[streamHtml] unexpected error", e);
                markFailedAndSettle(task, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public AiTranslateImageResponse translateImage(AiTranslateImageRequest request, SystemUser owner) throws Exception {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();
        AsyncTask task = createRealtimeTask(TranslationContentType.IMAGE, targetLang, "图片翻译",
                request.getAiAccountId(), owner);

        try {
            ImageData imageData = loadImageBytes(request);

            byte[] translated = geminiTranslateService.translateImageWithAccount(
                    account, request.getPrompt(), imageData.bytes, imageData.mimeType, targetLang,
                    usage -> recordUsage(task.getId(), account, TranslationContentType.IMAGE, targetLang, usage, owner));

            markCompletedAndSettle(task);

            if (translated == null) {
                if (imageData.sourceFile != null) {
                    return AiTranslateImageResponse.builder()
                            .id(imageData.sourceFile.getId())
                            .name(imageData.sourceFile.getName())
                            .suffix(imageData.sourceFile.getSuffix())
                            .mediaType(imageData.sourceFile.getMediaType() != null ? imageData.sourceFile.getMediaType().name() : "IMAGE")
                            .relativePath(imageData.sourceFile.getRelativePath())
                            .absolutionPath(MultimediaUtil.resolveAbsolutionPath(imageData.sourceFile.getId()))
                            .build();
                }
                return AiTranslateImageResponse.builder()
                        .id(null)
                        .name("no_translation_needed")
                        .suffix(imageData.suffix)
                        .mediaType("IMAGE")
                        .build();
            }

            MultimediaFile saved = multimediaFileService.saveTranslatedImage(translated, imageData.suffix, owner);

            return AiTranslateImageResponse.builder()
                    .id(saved.getId())
                    .name(saved.getName())
                    .suffix(saved.getSuffix())
                    .mediaType(saved.getMediaType() != null ? saved.getMediaType().name() : "IMAGE")
                    .relativePath(saved.getRelativePath())
                    .absolutionPath(MultimediaUtil.resolveAbsolutionPath(saved.getId()))
                    .build();
        } catch (Exception e) {
            markFailedAndSettle(task, e.getMessage());
            throw e;
        }
    }

    // ======================== 图片源三选一解析 ========================

    private record ImageData(byte[] bytes, String suffix, String mimeType, MultimediaFile sourceFile) {}

    private ImageData loadImageBytes(AiTranslateImageRequest request) throws Exception {
        // 1. multimediaFileId
        if (StrUtil.isNotBlank(request.getMultimediaFileId())) {
            return loadFromMultimediaFile(request.getMultimediaFileId());
        }
        // 2. imageDataBase64
        if (StrUtil.isNotBlank(request.getImageDataBase64())) {
            return loadFromBase64(request.getImageDataBase64());
        }
        // 3. imageUrl
        if (StrUtil.isNotBlank(request.getImageUrl())) {
            return loadFromUrl(request.getImageUrl());
        }
        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("multimediaFileId、imageUrl、imageDataBase64 至少需要提供一个");
    }

    private ImageData loadFromMultimediaFile(String fileId) throws Exception {
        MultimediaFile sourceFile = multimediaFileService.getById(Long.parseLong(fileId));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(sourceFile, "图片资源不存在: " + fileId);
        byte[] imageBytes;
        try (InputStream is = multimediaFileService.download(fileId, 0)) {
            imageBytes = is.readAllBytes();
        }
        String suffix = sourceFile.getSuffix();
        String mimeType = "image/" + (suffix.equalsIgnoreCase("jpg") ? "jpeg" : suffix.toLowerCase());
        return new ImageData(imageBytes, suffix, mimeType, sourceFile);
    }

    private ImageData loadFromBase64(String base64Data) {
        String suffix = "png";
        String rawBase64 = base64Data;
        if (base64Data.contains(",")) {
            String header = base64Data.substring(0, base64Data.indexOf(","));
            rawBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
            Matcher m = Pattern.compile("image/(\\w+)").matcher(header);
            if (m.find()) {
                suffix = m.group(1).equals("jpeg") ? "jpg" : m.group(1);
            }
        }
        byte[] imageBytes = Base64.getDecoder().decode(rawBase64);
        String mimeType = "image/" + (suffix.equalsIgnoreCase("jpg") ? "jpeg" : suffix);
        return new ImageData(imageBytes, suffix, mimeType, null);
    }

    private ImageData loadFromUrl(String imageUrl) throws Exception {
        Matcher m = TRAILING_ID_PATTERN.matcher(imageUrl);
        if (m.find()) {
            String id = m.group(1);
            try {
                MultimediaFile file = multimediaFileService.getById(Long.parseLong(id));
                if (file != null) {
                    return loadFromMultimediaFile(id);
                }
            } catch (Exception e) {
                log.debug("[loadFromUrl] 尝试按 ID={} 反查失败，改用 HTTP 下载: {}", id, e.getMessage());
            }
        }
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP_CLIENT.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("下载图片失败: HTTP " + resp.statusCode());
        }
        byte[] imageBytes = resp.body();
        String contentType = resp.headers().firstValue("content-type").orElse("image/png");
        String suffix = "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) suffix = "jpg";
        else if (contentType.contains("webp")) suffix = "webp";
        else if (contentType.contains("gif")) suffix = "gif";
        else if (contentType.contains("svg")) suffix = "svg";
        String mimeType = "image/" + (suffix.equals("jpg") ? "jpeg" : suffix);
        return new ImageData(imageBytes, suffix, mimeType, null);
    }

    // ======================== 工具方法 ========================

    private AiAccount resolveAccount(String aiAccountId) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(aiAccountId, "AI账号ID不能为空");
        AiAccount account = aiAccountService.getById(Long.parseLong(aiAccountId));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(account, "AI账号不存在");
        if (account.getProvider() != AiProvider.GEMINI_OFFICIAL_STANDARD) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("仅支持 Gemini 官方标准账号进行实时翻译");
        }
        return account;
    }

    private Language resolveLanguage(String languageId) {
        Language language = languageService.getById(Long.parseLong(languageId));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(language, "目标语言不存在");
        return language;
    }

    // ======================== AsyncTask 生命周期 ========================

    private AsyncTask createRealtimeTask(TranslationContentType contentType,
                                         String targetLang,
                                         String contentPreview,
                                         String aiAccountId,
                                         SystemUser owner) {
        AsyncTask task = AsyncTask.builder()
                .taskType(TaskType.PRODUCT_AI_REALTIME_TRANSLATE)
                .state(TaskState.PROCESSING)
                .progress(0)
                .estimatedCredits(0)
                .parameters("{\"contentType\":\"" + contentType + "\",\"targetLanguage\":\""
                        + targetLang + "\",\"aiAccountId\":\"" + aiAccountId + "\"}")
                .name("实时翻译: " + contentPreview + " → " + targetLang)
                .build();
        task.setOwner(owner);
        return asyncTaskRepository.saveAndFlush(task);
    }

    private void markCompletedAndSettle(AsyncTask task) {
        try {
            asyncTaskService.updateAsyncTask(task, TaskState.COMPLETED, 100);
            asyncTaskService.finalizeBilling(task.getId());
        } catch (Exception e) {
            log.warn("[markCompletedAndSettle] taskId={} 结算失败", task.getId(), e);
        }
    }

    private void markFailedAndSettle(AsyncTask task, String errorMsg) {
        try {
            task.setMessage(errorMsg);
            asyncTaskService.updateAsyncTask(task, TaskState.FAILED, 100);
            asyncTaskService.finalizeBilling(task.getId());
        } catch (Exception e) {
            log.warn("[markFailedAndSettle] taskId={} 结算失败", task.getId(), e);
        }
    }

    private void recordUsage(Long taskId, AiAccount account, TranslationContentType contentType,
                             String targetLang, GeminiTranslateService.TokenUsage usage,
                             SystemUser owner) {
        try {
            int prompt = safeInt(usage.getPromptTokens());
            int completion = safeInt(usage.getCompletionTokens());
            int thinking = safeInt(usage.getThinkingTokens());
            int total = safeInt(usage.getTotalTokens());
            BigDecimal cost = TokenCostCalculator.calculateCost(contentType, account, prompt, completion, thinking);
            int credits = TokenCostCalculator.usdToCredits(cost);

            AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                    .taskId(taskId)
                    .subTaskId(UUID.randomUUID().toString())
                    .aiAccount(account)
                    .contentType(contentType)
                    .targetLanguage(targetLang)
                    .cacheHit(false)
                    .skipped(false)
                    .model(account.getModel())
                    .actualPromptTokens(prompt)
                    .actualCompletionTokens(completion)
                    .actualThinkingTokens(thinking)
                    .actualTotalTokens(total)
                    .businessPromptTokens(prompt)
                    .businessCompletionTokens(completion)
                    .businessThinkingTokens(thinking)
                    .businessTotalTokens(prompt + completion + thinking)
                    .businessCost(cost)
                    .businessCredits(credits)
                    .elapsedMs(usage.getElapsedMs())
                    .settled(false)
                    .build();
            if (owner != null) {
                record.setOwner(owner);
            }
            usageRecordRepository.saveAndFlush(record);
        } catch (Exception e) {
            log.warn("[recordUsage] 记录 token 用量失败", e);
        }
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        String stripped = text.replaceAll("<[^>]*>", "").strip();
        return stripped.length() <= maxLen ? stripped : stripped.substring(0, maxLen) + "…";
    }
}
