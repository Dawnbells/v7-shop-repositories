package cn.v7soft.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;

import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import cn.v7soft.admin.exception.GeminiContentBlockedException;
import com.google.genai.errors.ApiException;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;

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
