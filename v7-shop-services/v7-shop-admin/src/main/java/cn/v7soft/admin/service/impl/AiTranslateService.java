package cn.v7soft.admin.service.impl;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import cn.v7soft.admin.service.AiTranslationClient;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.dao.entities.primary.AiAccount;

@Service
public class AiTranslateService {

    private final List<AiTranslationClient> clients;

    public AiTranslateService(List<AiTranslationClient> clients) {
        this.clients = clients;
    }

    public String translateTextRaw(AiAccount account, String text, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        return getClient(account).translateTextRaw(account, text, targetLanguageName, usageCallback);
    }

    public String translateHtmlRaw(AiAccount account, String html, String targetLanguageName,
                                   Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        return getClient(account).translateHtmlRaw(account, html, targetLanguageName, usageCallback);
    }

    public byte[] translateImageRaw(AiAccount account, byte[] imageBytes, String mimeType, String targetLanguageName,
                                    Consumer<GeminiTranslateService.TokenUsage> usageCallback) {
        return getClient(account).translateImageRaw(account, imageBytes, mimeType, targetLanguageName, usageCallback);
    }

    public String getModel(AiAccount account) {
        return getClient(account).getModel(account);
    }

    private AiTranslationClient getClient(AiAccount account) {
        if (account == null || account.getProvider() == null) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("AI账号服务商不能为空");
        }
        return clients.stream()
                .filter(client -> client.supports(account.getProvider()))
                .findFirst()
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("不支持的AI服务商"));
    }
}
