package cn.v7soft.admin.service.impl;

import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiProvider;

@Service
public class AiTranslateService {

    private final GeminiTranslateService geminiTranslateService;
    private final OpenAiTranslateService openAiTranslateService;

    public AiTranslateService(GeminiTranslateService geminiTranslateService,
                              OpenAiTranslateService openAiTranslateService) {
        this.geminiTranslateService = geminiTranslateService;
        this.openAiTranslateService = openAiTranslateService;
    }

    public String translateTextRaw(AiAccount account, String text, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        if (account.getProvider() == AiProvider.GEMINI) {
            return geminiTranslateService.translateTextRaw(account, text, targetLanguageName, usageCallback);
        }
        if (account.getProvider() == AiProvider.OPENAI) {
            return openAiTranslateService.translateTextRaw(account, text, targetLanguageName, usageCallback);
        }
        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("不支持的AI服务商");
    }

    public String translateHtmlRaw(AiAccount account, String html, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        if (account.getProvider() == AiProvider.GEMINI) {
            return geminiTranslateService.translateHtmlRaw(account, html, targetLanguageName, usageCallback);
        }
        if (account.getProvider() == AiProvider.OPENAI) {
            return openAiTranslateService.translateHtmlRaw(account, html, targetLanguageName, usageCallback);
        }
        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("不支持的AI服务商");
    }

    public byte[] translateImageRaw(AiAccount account, byte[] imageBytes, String mimeType, String targetLanguageName,
                                    Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        if (account.getProvider() == AiProvider.GEMINI) {
            return geminiTranslateService.translateImageRaw(account, imageBytes, mimeType, targetLanguageName, usageCallback);
        }
        if (account.getProvider() == AiProvider.OPENAI) {
            return openAiTranslateService.translateImageRaw(account, imageBytes, mimeType, targetLanguageName, usageCallback);
        }
        throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("不支持的AI服务商");
    }

    public String getModel(AiAccount account) {
        if (account != null && account.getModel() != null && !account.getModel().isBlank()) {
            return account.getModel();
        }
        return geminiTranslateService.getModel();
    }
}
