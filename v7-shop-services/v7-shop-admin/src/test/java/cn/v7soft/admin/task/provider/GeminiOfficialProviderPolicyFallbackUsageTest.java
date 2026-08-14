package cn.v7soft.admin.task.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import cn.v7soft.admin.exception.GeminiContentBlockedException;
import cn.v7soft.admin.service.IAiAccountService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.impl.GeminiTranslateService;
import cn.v7soft.admin.task.AiAccountTranslateSubTask;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.entities.primary.Language;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class GeminiOfficialProviderPolicyFallbackUsageTest {

    @Mock private GeminiTranslateService geminiTranslateService;
    @Mock private IAiAccountService aiAccountService;
    @Mock private ILanguageService languageService;
    @Mock private IMultimediaFileService multimediaFileService;
    @Mock private ThreadPoolTaskExecutor executor;
    @Mock private TranslateProviderCallback callback;

    @Test
    void blockedTextRetainsTheActualUsageEmittedBeforeTheException() {
        AiAccount account = AiAccount.builder().id(7L).build();
        when(aiAccountService.getById(7L)).thenReturn(account);
        when(languageService.getById(1L))
                .thenReturn(Language.builder().id(1L).name("French").code("fr").build());
        when(executor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        when(geminiTranslateService.translateTextRaw(eq("hello"), eq("French"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Consumer<GeminiTranslateService.TokenUsage> usageCallback = invocation.getArgument(2);
                    usageCallback.accept(GeminiTranslateService.TokenUsage.builder()
                            .promptTokens(11)
                            .completionTokens(3)
                            .thinkingTokens(2)
                            .totalTokens(16)
                            .elapsedMs(321L)
                            .build());
                    throw new GeminiContentBlockedException("SAFETY", "blocked");
                });

        RateLimiter rateLimiter = RateLimiter.of(
                "policy-fallback-usage",
                RateLimiterConfig.custom()
                        .limitForPeriod(10)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
        GeminiOfficialProvider provider = new GeminiOfficialProvider(
                geminiTranslateService,
                aiAccountService,
                languageService,
                multimediaFileService,
                executor,
                rateLimiter);
        provider.setCallback(callback);

        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("1");
        request.setCountryId("1");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.text(
                1L, "text-hash", "hello", request);

        provider.executeSubTask(subTask);

        ArgumentCaptor<SubTaskResult> resultCaptor = ArgumentCaptor.forClass(SubTaskResult.class);
        verify(callback).onSubTaskCompleted(eq(subTask), resultCaptor.capture());
        SubTaskResult result = resultCaptor.getValue();
        assertEquals("SAFETY", result.getPolicyFallbackReason());
        assertEquals(11, result.getActualPromptTokens());
        assertEquals(3, result.getActualCompletionTokens());
        assertEquals(2, result.getActualThinkingTokens());
        assertEquals(11, result.getBusinessPromptTokens());
        assertEquals(3, result.getBusinessCompletionTokens());
        assertEquals(2, result.getBusinessThinkingTokens());
        assertEquals(321L, result.getElapsedMs());
    }
}
