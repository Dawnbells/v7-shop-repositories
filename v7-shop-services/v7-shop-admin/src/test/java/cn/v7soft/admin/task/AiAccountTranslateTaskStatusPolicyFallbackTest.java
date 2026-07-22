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
        status.completePolicyFallbackImageSubTask(subTask);

        assertEquals(1, status.getCompletedSubTaskCount().get());
        assertEquals(1, status.getPolicyFallbackImageCount().get());
        assertTrue(status.getTranslatedImageMap().isEmpty());
        assertEquals("翻译完成，1 张图片因内容政策限制保留原图", status.buildCompletionMessage());
    }
}
