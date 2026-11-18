package cn.v7soft.admin.service.impl;

import static cn.v7soft.admin.service.impl.GeminiQuotaTracker.ReservationStatus.ACQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import cn.v7soft.admin.configurer.TranslationExecutorConfig;
import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import cn.v7soft.admin.exception.GeminiContentBlockedException;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentResponse;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Gemini 错误分档按官方文档的 Retryable 列，不靠猜：
 * https://ai.google.dev/gemini-api/docs/api-errors
 */
class GeminiTranslateServiceErrorPolicyTest {

    @Test
    void documentedRetryableStatusesAreRetried() {
        for (int code : new int[] {408, 409, 429, 500, 502, 503, 504}) {
            assertTrue(GeminiTranslateService.isRetryableApiError(apiException(code)),
                    "HTTP " + code + " 文档标记可重试");
        }
    }

    @Test
    void documentedPermanentStatusesAreNotRetried() {
        for (int code : new int[] {400, 401, 403, 404, 416, 499, 501}) {
            assertFalse(GeminiTranslateService.isRetryableApiError(apiException(code)),
                    "HTTP " + code + " 文档标记 Retryable=No，重试没有意义");
        }
    }

    @Test
    void networkFailuresWithoutAnHttpStatusAreRetried() {
        assertTrue(GeminiTranslateService.isRetryableApiError(new SocketTimeoutException("read timed out")));
        assertTrue(GeminiTranslateService.isRetryableApiError(new IOException("connection reset")));
    }

    @Test
    void unknownLocalRuntimeFailuresAreNotRetriedForever() {
        assertFalse(GeminiTranslateService.isRetryableApiError(
                new IllegalArgumentException("invalid language id")));
        assertFalse(GeminiTranslateService.isRetryableApiError(
                new NullPointerException("invalid local state")));
    }

    @Test
    void dailyQuotaExhaustionIsRetriedBecauseItRecoversOnItsOwn() {
        assertTrue(GeminiTranslateService.isRetryableApiError(
                new DailyQuotaExhaustedException("所有 API Key 今日配额已耗尽")));
    }

    @Test
    void dailyQuota429IsDistinguishedFromMinuteRateLimits() {
        for (String quota : List.of("requests_per_day", "GenerateRequestsPerDayPerProjectPerModel", "daily quota")) {
            ApiException error = new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded: " + quota);
            assertTrue(GeminiTranslateService.isDailyQuota429(error));
            assertFalse(TranslationExecutorConfig.isRetryable(error));
        }
        ApiException minuteLimit = new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded: requests_per_minute");
        assertFalse(GeminiTranslateService.isDailyQuota429(minuteLimit));
        assertTrue(TranslationExecutorConfig.isRetryable(minuteLimit));
    }

    @Test
    void keysWithTheSamePrefixUseSeparateQuotaCounters() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), anyString(), anyString()))
                .thenReturn(1L);
        GeminiQuotaTracker tracker = new GeminiQuotaTracker(1000, " fixture-A , fixture-B, ,fixture-A", redis);

        assertEquals(List.of("fixture-A", "fixture-B"), tracker.getApiKeys());
        assertNotEquals(tracker.reserve("fixture-A").quotaKey(), tracker.reserve("fixture-B").quotaKey());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 所有 Key 均已达到服务端日上限, true",
            "-1, -1, Gemini 已返回日配额耗尽, false",
            "0, -1, 部分 Key 达到服务端日上限, true"
    })
    void exhaustedQuotaReportsItsSource(long firstResult, long secondResult, String message, boolean includesLimit) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(ArgumentMatchers.<RedisScript<Long>>any(), anyList(), eq("50000"), anyString()))
                .thenReturn(firstResult, secondResult);
        GeminiQuotaTracker tracker = new GeminiQuotaTracker(50000, "fixture-A,fixture-B", redis);
        GeminiTranslateService service = new GeminiTranslateService("", 0, tracker, mock(Retry.class));

        DailyQuotaExhaustedException error = assertThrows(DailyQuotaExhaustedException.class,
                () -> service.callGemini(client -> fail("Gemini should not be called for an exhausted key")));

        assertTrue(error.getMessage().contains(message));
        assertEquals(includesLimit, error.getMessage().contains("50000"));
    }

    @Test
    void translationTriesThirdKeyWhenFirstTwoReportDailyQuotaExhaustion() {
        List<String> keys = List.of("fixture-A", "fixture-B", "fixture-C");
        GeminiQuotaTracker tracker = mock(GeminiQuotaTracker.class);
        when(tracker.getApiKeys()).thenReturn(keys);
        Instant resetAt = Instant.now().plusSeconds(60);
        var first = new GeminiQuotaTracker.Reservation("first", resetAt, "hashA", ACQUIRED);
        var second = new GeminiQuotaTracker.Reservation("second", resetAt, "hashB", ACQUIRED);
        var third = new GeminiQuotaTracker.Reservation("third", resetAt, "hashC", ACQUIRED);
        when(tracker.reserve(keys.get(0))).thenReturn(first);
        when(tracker.reserve(keys.get(1))).thenReturn(second);
        when(tracker.reserve(keys.get(2))).thenReturn(third);
        GeminiTranslateService service = new GeminiTranslateService("", 0, tracker, mock(Retry.class));
        List<String> attemptedKeys = new ArrayList<>();

        String translated = service.callGemini(client -> {
            attemptedKeys.add(client.apiKey());
            if (!keys.get(2).equals(client.apiKey())) {
                throw new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded: requests_per_day");
            }
            return "translated";
        });

        assertEquals(keys, attemptedKeys);
        assertEquals("translated", translated);
        verify(tracker).markExhausted(first);
        verify(tracker).markExhausted(second);
    }

    @Test
    void minuteRateLimitRefundsTheReservationAndPreservesTheApiError() {
        String apiKey = "fixture-A";
        GeminiQuotaTracker tracker = mock(GeminiQuotaTracker.class);
        when(tracker.getApiKeys()).thenReturn(List.of(apiKey));
        var reservation = new GeminiQuotaTracker.Reservation("first", Instant.now().plusSeconds(60), "hashA", ACQUIRED);
        when(tracker.reserve(apiKey)).thenReturn(reservation);
        GeminiTranslateService service = new GeminiTranslateService("", 0, tracker, mock(Retry.class));
        ApiException minuteLimit = new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded: requests_per_minute");

        assertSame(minuteLimit, assertThrows(ApiException.class, () -> service.callGemini(client -> {
            throw minuteLimit;
        })));
        verify(tracker).getApiKeys();
        verify(tracker).reserve(apiKey);
        verify(tracker).refund(reservation);
        verifyNoMoreInteractions(tracker);
    }

    @Test
    void blankApiKeyConfigurationDoesNotConsumeQuota() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GeminiQuotaTracker tracker = new GeminiQuotaTracker(1000, " , ", redis);
        GeminiTranslateService service = new GeminiTranslateService("", 0, tracker, mock(Retry.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.translateTextRaw("A product title", "French"));

        assertTrue(error.getMessage().contains("GEMINI_API_KEY"));
        verifyNoInteractions(redis);
    }

    @Test
    void wrappedApiExceptionsAreUnwrappedBeforeClassifying() {
        assertFalse(GeminiTranslateService.isRetryableApiError(
                new RuntimeException("translate failed", apiException(401))));
        assertTrue(GeminiTranslateService.isRetryableApiError(
                new RuntimeException("translate failed", apiException(503))));
    }

    @Test
    void accountImageResponsesUseTheSameContentPolicyValidation() {
        GenerateContentResponse blocked = GenerateContentResponse.fromJson(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        GeminiContentBlockedException error = assertThrows(
                GeminiContentBlockedException.class,
                () -> GeminiTranslateService.extractUsableImageResult(
                        blocked, "translateImageWithAccount"));

        assertEquals("SAFETY", error.getReason());
    }

    private static ApiException apiException(int code) {
        return new ApiException(code, "HTTP " + code, null);
    }
}
