package cn.v7soft.admin.service;

import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;

import java.util.function.Consumer;

public interface AiTranslationClient {

    boolean supports(AiProvider provider);

    String translateTextRaw(AiAccount account, String text, String targetLanguageName,
                            Consumer<GeminiTranslateService.TokenUsage> usageCallback);

    String translateHtmlRaw(AiAccount account, String html, String targetLanguageName,
                            Consumer<GeminiTranslateService.TokenUsage> usageCallback);

    byte[] translateImageRaw(AiAccount account, byte[] imageBytes, String mimeType, String targetLanguageName,
                             Consumer<GeminiTranslateService.TokenUsage> usageCallback);

    String getModel(AiAccount account);
}
