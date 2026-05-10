package cn.v7soft.admin.service.impl;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cn.v7soft.admin.controller.req.AiTranslateHtmlRequest;
import cn.v7soft.admin.controller.req.AiTranslateImageRequest;
import cn.v7soft.admin.controller.req.AiTranslateTextRequest;
import cn.v7soft.admin.controller.resp.AiTranslateImageResponse;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.IAiTranslateService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.utils.MultimediaUtil;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.AiTokenUsageRecord;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.TranslationContentType;
import cn.v7soft.dao.repositories.primary.AiTokenUsageRecordRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.utils.SaSessionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiTranslateService implements IAiTranslateService {

    private static final long REALTIME_TASK_ID = -1L;

    private final GeminiTranslateService geminiTranslateService;
    private final IAiAccountService aiAccountService;
    private final ILanguageService languageService;
    private final IMultimediaFileService multimediaFileService;
    private final AiTokenUsageRecordRepository usageRecordRepository;
    private final SystemUserRepository systemUserRepository;
    private final ThreadPoolTaskExecutor translationExecutor;

    public AiTranslateService(GeminiTranslateService geminiTranslateService,
                              IAiAccountService aiAccountService,
                              ILanguageService languageService,
                              IMultimediaFileService multimediaFileService,
                              AiTokenUsageRecordRepository usageRecordRepository,
                              SystemUserRepository systemUserRepository,
                              @Qualifier("translationExecutor") ThreadPoolTaskExecutor translationExecutor) {
        this.geminiTranslateService = geminiTranslateService;
        this.aiAccountService = aiAccountService;
        this.languageService = languageService;
        this.multimediaFileService = multimediaFileService;
        this.usageRecordRepository = usageRecordRepository;
        this.systemUserRepository = systemUserRepository;
        this.translationExecutor = translationExecutor;
    }

    @Override
    public void streamText(AiTranslateTextRequest request, SseEmitter emitter) {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();

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
                        usage -> recordUsage(account, TranslationContentType.TEXT, targetLang, usage),
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("[streamText] emitter.complete failed", e);
                            }
                        },
                        error -> {
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
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void streamHtml(AiTranslateHtmlRequest request, SseEmitter emitter) {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();

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
                        usage -> recordUsage(account, TranslationContentType.HTML, targetLang, usage),
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("[streamHtml] emitter.complete failed", e);
                            }
                        },
                        error -> {
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
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public AiTranslateImageResponse translateImage(AiTranslateImageRequest request) throws Exception {
        AiAccount account = resolveAccount(request.getAiAccountId());
        Language language = resolveLanguage(request.getLanguageId());
        String targetLang = language.getName();

        MultimediaFile sourceFile = multimediaFileService.getById(Long.parseLong(request.getMultimediaFileId()));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(sourceFile, "原始图片不存在");

        byte[] imageBytes;
        try (InputStream is = multimediaFileService.download(request.getMultimediaFileId(), 0)) {
            imageBytes = is.readAllBytes();
        }

        String mimeType = "image/" + (sourceFile.getSuffix().equalsIgnoreCase("jpg") ? "jpeg" : sourceFile.getSuffix().toLowerCase());

        byte[] translated = geminiTranslateService.translateImageWithAccount(
                account, request.getPrompt(), imageBytes, mimeType, targetLang,
                usage -> recordUsage(account, TranslationContentType.IMAGE, targetLang, usage));

        if (translated == null) {
            return AiTranslateImageResponse.builder()
                    .id(sourceFile.getId())
                    .name(sourceFile.getName())
                    .suffix(sourceFile.getSuffix())
                    .mediaType(sourceFile.getMediaType() != null ? sourceFile.getMediaType().name() : "IMAGE")
                    .relativePath(sourceFile.getRelativePath())
                    .absolutionPath(MultimediaUtil.resolveAbsolutionPath(sourceFile.getId()))
                    .build();
        }

        Long userId = SaSessionUtil.getLoginUser().getLongId();
        SystemUser owner = systemUserRepository.findById(userId).orElse(null);

        MultimediaFile saved = multimediaFileService.saveTranslatedImage(translated, sourceFile.getSuffix(), owner);

        return AiTranslateImageResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .suffix(saved.getSuffix())
                .mediaType(saved.getMediaType() != null ? saved.getMediaType().name() : "IMAGE")
                .relativePath(saved.getRelativePath())
                .absolutionPath(MultimediaUtil.resolveAbsolutionPath(saved.getId()))
                .build();
    }

    private AiAccount resolveAccount(String aiAccountId) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notBlank(aiAccountId, "AI账号ID不能为空");
        AiAccount account = aiAccountService.getById(Long.parseLong(aiAccountId));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(account, "AI账号不存在");
        return account;
    }

    private Language resolveLanguage(String languageId) {
        Language language = languageService.getById(Long.parseLong(languageId));
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(language, "目标语言不存在");
        return language;
    }

    private void recordUsage(AiAccount account, TranslationContentType contentType,
                             String targetLang, GeminiTranslateService.TokenUsage usage) {
        try {
            AiTokenUsageRecord record = AiTokenUsageRecord.builder()
                    .taskId(REALTIME_TASK_ID)
                    .subTaskId("realtime-" + System.currentTimeMillis())
                    .aiAccount(account)
                    .contentType(contentType)
                    .targetLanguage(targetLang)
                    .cacheHit(false)
                    .skipped(false)
                    .model(account.getModel())
                    .actualPromptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0)
                    .actualCompletionTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0)
                    .actualThinkingTokens(usage.getThinkingTokens() != null ? usage.getThinkingTokens() : 0)
                    .actualTotalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens() : 0)
                    .elapsedMs(usage.getElapsedMs())
                    .settled(true)
                    .build();
            record.fillOwner();
            usageRecordRepository.saveAndFlush(record);
        } catch (Exception e) {
            log.warn("[recordUsage] 记录 token 用量失败", e);
        }
    }
}
