package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.v7soft.admin.controller.req.TranslateByAIRequest;
import org.junit.jupiter.api.Test;

class AiAccountTranslateTaskStatusPolicyFallbackTest {

    @Test
    void policyFallbackCompletesImageAndBuildsFriendlyCompletionMessage() {
        AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(
                1L, 1, 10L, null, 20L, null, 30L);
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("10");
        request.setCountryId("20");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask subTask = AiAccountTranslateSubTask.image(1L, "99", request);

        status.dispatchSubTask(subTask);
        status.completePolicyFallbackSubTask(subTask);

        assertEquals(1, status.getCompletedSubTaskCount().get());
        assertEquals(1, status.getPolicyFallbackCount().get());
        assertTrue(status.getTranslatedImageMap().isEmpty());
        assertEquals("翻译完成，1 项内容因政策限制保留原文/原图", status.buildCompletionMessage());
    }

    @Test
    void policyFallbackOnTextKeepsOriginalAndSharesTheSameCounter() {
        AiAccountTranslateTaskStatus status = new AiAccountTranslateTaskStatus(
                2L, 2, 10L, null, 20L, null, 30L);
        TranslateByAIRequest request = new TranslateByAIRequest();
        request.setProductId("10");
        request.setCountryId("20");
        request.setLanguageId("1");
        request.setAiAccountId("7");
        AiAccountTranslateSubTask textSubTask = AiAccountTranslateSubTask.text(2L, "hash-a", "some text", request);
        AiAccountTranslateSubTask imageSubTask = AiAccountTranslateSubTask.image(2L, "99", request);

        status.dispatchSubTask(textSubTask);
        status.completePolicyFallbackSubTask(textSubTask);
        status.dispatchSubTask(imageSubTask);
        status.completePolicyFallbackSubTask(imageSubTask);

        assertEquals(2, status.getCompletedSubTaskCount().get());
        assertEquals(0, status.getFailedSubTaskCount().get());
        assertEquals(2, status.getPolicyFallbackCount().get());
        // 不写译文/译图产物 —— ProductService 组装时自动回落原件
        assertTrue(status.getTranslatedTextMap().isEmpty());
        assertTrue(status.getTranslatedImageMap().isEmpty());
        assertEquals("翻译完成，2 项内容因政策限制保留原文/原图", status.buildCompletionMessage());
    }
}
