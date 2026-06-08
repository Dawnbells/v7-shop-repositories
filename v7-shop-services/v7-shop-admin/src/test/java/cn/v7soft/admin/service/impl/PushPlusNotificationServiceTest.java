package cn.v7soft.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SavePushPlusNotificationConfigRequest;
import cn.v7soft.admin.event.AiTranslateTaskNotificationEvent;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.pushplus.PushPlusClient;
import cn.v7soft.admin.service.pushplus.PushPlusSendRequest;
import cn.v7soft.admin.service.pushplus.PushPlusSendResponse;

class PushPlusNotificationServiceTest {

    @Test
    void skipsAiTranslateNotificationWhenConfigClosed() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", false);
        configService.value.set("token", "push-token");
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);

        service.sendAiTranslateTaskNotification(submitEvent());

        assertThat(client.requests).isEmpty();
    }

    @Test
    void skipsAiTranslateNotificationWhenTokenMissing() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", true);
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);

        service.sendAiTranslateTaskNotification(submitEvent());

        assertThat(client.requests).isEmpty();
    }

    @Test
    void sendsMarkdownAiTranslateSubmitMessageWhenEnabled() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", true);
        configService.value.set("token", "push-token");
        configService.value.set("template", "markdown");
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);

        service.sendAiTranslateTaskNotification(submitEvent());

        assertThat(client.requests).hasSize(1);
        PushPlusSendRequest request = client.requests.get(0);
        assertThat(request.getToken()).isEqualTo("push-token");
        assertThat(request.getTitle()).isEqualTo("AI翻译任务已提交");
        assertThat(request.getChannel()).isEqualTo("wechat");
        assertThat(request.getTemplate()).isEqualTo("markdown");
        assertThat(request.getContent())
                .contains("提交人：张三")
                .contains("商品：测试商品")
                .contains("目标国家：Japan")
                .contains("目标语言：Japanese")
                .contains("AI账号：Gemini账号")
                .contains("任务ID：1001");
    }

    @Test
    void sendsRetryMessageWithOriginalTaskId() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", true);
        configService.value.set("token", "push-token");
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);

        service.sendAiTranslateTaskNotification(AiTranslateTaskNotificationEvent.retry(
                1L,
                1000L,
                1002L,
                "李四",
                "重试商品",
                "Thailand",
                "Thai",
                "TurboFlow账号",
                LocalDateTime.of(2026, 6, 8, 10, 20, 30)
        ));

        assertThat(client.requests).hasSize(1);
        PushPlusSendRequest request = client.requests.get(0);
        assertThat(request.getTitle()).isEqualTo("AI翻译任务已重试");
        assertThat(request.getContent())
                .contains("重试人：李四")
                .contains("原任务ID：1000")
                .contains("新任务ID：1002");
    }

    @Test
    void pushPlusFailureDoesNotEscapeBusinessNotification() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", true);
        configService.value.set("token", "push-token");
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        client.response = PushPlusSendResponse.builder().code(500).msg("failed").build();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);

        assertThatCode(() -> service.sendAiTranslateTaskNotification(submitEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void saveConfigPreservesExistingTokenWhenRequestTokenBlank() {
        FakeDynamicConfigService configService = new FakeDynamicConfigService();
        configService.value.set("open", true);
        configService.value.set("token", "old-token");
        RecordingPushPlusClient client = new RecordingPushPlusClient();
        PushPlusNotificationService service = new PushPlusNotificationService(configService, client);
        SavePushPlusNotificationConfigRequest request = new SavePushPlusNotificationConfigRequest();
        request.setOpen(false);
        request.setToken(" ");
        request.setTemplate("markdown");

        service.saveConfig(request);

        assertThat(configService.saved.getBool("open")).isFalse();
        assertThat(configService.saved.getStr("token")).isEqualTo("old-token");
        assertThat(configService.saved.getStr("template")).isEqualTo("markdown");
    }

    private static AiTranslateTaskNotificationEvent submitEvent() {
        return AiTranslateTaskNotificationEvent.submitted(
                1L,
                1001L,
                "张三",
                "测试商品",
                "Japan",
                "Japanese",
                "Gemini账号",
                LocalDateTime.of(2026, 6, 8, 9, 10, 11)
        );
    }

    private static class RecordingPushPlusClient implements PushPlusClient {
        private final List<PushPlusSendRequest> requests = new ArrayList<>();
        private PushPlusSendResponse response = PushPlusSendResponse.builder()
                .code(200)
                .msg("请求成功")
                .data("short-code")
                .build();

        @Override
        public PushPlusSendResponse send(PushPlusSendRequest request) {
            requests.add(request);
            return response;
        }
    }

    private static class FakeDynamicConfigService implements IDynamicConfigService {
        private final JSONObject value = new JSONObject();
        private JSONObject saved;

        @Override
        public Optional<JSONObject> getConfigWithFallback(String configName, Long departmentId, Long companyId) {
            return Optional.of(value);
        }

        @Override
        public JSONObject getConfigValue(String configName, Long departmentId, Long companyId) {
            return value;
        }

        @Override
        public void saveConfig(String configName, Long departmentId, JSONObject configValue) {
            this.saved = configValue;
        }
    }
}
