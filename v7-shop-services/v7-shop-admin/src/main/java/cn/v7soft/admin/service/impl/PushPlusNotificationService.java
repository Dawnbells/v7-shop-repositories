package cn.v7soft.admin.service.impl;

import java.time.format.DateTimeFormatter;
import java.util.Set;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.SavePushPlusNotificationConfigRequest;
import cn.v7soft.admin.controller.resp.PushPlusNotificationConfigResponse;
import cn.v7soft.admin.controller.resp.PushPlusNotificationTestResponse;
import cn.v7soft.admin.event.AiTranslateTaskNotificationEvent;
import cn.v7soft.admin.event.ServerIpSwitchNotificationEvent;
import cn.v7soft.admin.service.IDynamicConfigService;
import cn.v7soft.admin.service.IPushPlusNotificationService;
import cn.v7soft.admin.service.pushplus.PushPlusClient;
import cn.v7soft.admin.service.pushplus.PushPlusSendRequest;
import cn.v7soft.admin.service.pushplus.PushPlusSendResponse;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushPlusNotificationService implements IPushPlusNotificationService {

    public static final String CONFIG_NAME = "pushplus-notification";
    private static final String DEFAULT_CHANNEL = "wechat";
    private static final String DEFAULT_TEMPLATE = "markdown";
    private static final String SUBMIT_TITLE = "AI翻译任务已提交";
    private static final String RETRY_TITLE = "AI翻译任务已重试";
    private static final String SERVER_FAILOVER_TITLE = "服务器IP已切换到备用IP";
    private static final String SERVER_RECOVERY_TITLE = "服务器IP已恢复到主IP";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SUPPORTED_TEMPLATES = Set.of("markdown", "html", "txt", "json");

    private final IDynamicConfigService dynamicConfigService;
    private final PushPlusClient pushPlusClient;

    @Override
    public PushPlusNotificationConfigResponse getConfig() {
        JSONObject config = getConfigValue(TenantContext.getCurrentTenant());
        return PushPlusNotificationConfigResponse.builder()
                .open(config.getBool("open", false))
                .serverIpSwitchOpen(config.getBool("serverIpSwitchOpen", false))
                .tokenSet(StrUtil.isNotBlank(config.getStr("token")))
                .template(normalizeTemplate(config.getStr("template")))
                .build();
    }

    @Override
    public void saveConfig(SavePushPlusNotificationConfigRequest request) {
        JSONObject existing = getConfigValue(TenantContext.getCurrentTenant());
        JSONObject config = new JSONObject();
        config.set("open", Boolean.TRUE.equals(request.getOpen()));
        config.set("serverIpSwitchOpen", request.getServerIpSwitchOpen() == null
                ? existing.getBool("serverIpSwitchOpen", false)
                : Boolean.TRUE.equals(request.getServerIpSwitchOpen()));
        config.set("template", normalizeTemplate(request.getTemplate()));
        if (StrUtil.isNotBlank(request.getToken())) {
            config.set("token", request.getToken().trim());
        } else if (StrUtil.isNotBlank(existing.getStr("token"))) {
            config.set("token", existing.getStr("token"));
        }
        dynamicConfigService.saveConfig(CONFIG_NAME, null, config);
    }

    @Override
    public PushPlusNotificationTestResponse sendTest(String content) {
        JSONObject config = getConfigValue(TenantContext.getCurrentTenant());
        String token = config.getStr("token");
        if (StrUtil.isBlank(token)) {
            return PushPlusNotificationTestResponse.builder()
                    .success(false)
                    .message("请先保存 PushPlus token")
                    .build();
        }

        PushPlusSendResponse response = pushPlusClient.send(PushPlusSendRequest.builder()
                .token(token)
                .title("PushPlus测试通知")
                .content(content)
                .channel(DEFAULT_CHANNEL)
                .template(normalizeTemplate(config.getStr("template")))
                .build());
        boolean success = response != null && Integer.valueOf(200).equals(response.getCode());
        return PushPlusNotificationTestResponse.builder()
                .success(success)
                .message(response == null ? "PushPlus 无响应" : response.getMsg())
                .shortCode(response == null ? null : response.getData())
                .build();
    }

    @Override
    public void sendAiTranslateTaskNotification(AiTranslateTaskNotificationEvent event) {
        try {
            JSONObject config = getConfigValue(event.getCompanyId());
            if (!config.getBool("open", false)) {
                return;
            }
            String token = config.getStr("token");
            if (StrUtil.isBlank(token)) {
                return;
            }
            PushPlusSendResponse response = pushPlusClient.send(PushPlusSendRequest.builder()
                    .token(token)
                    .title(event.isRetry() ? RETRY_TITLE : SUBMIT_TITLE)
                    .content(buildAiTranslateContent(event))
                    .channel(DEFAULT_CHANNEL)
                    .template(normalizeTemplate(config.getStr("template")))
                    .build());
            if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
                log.warn("PushPlus AI 翻译任务通知发送失败: taskId={}, code={}, msg={}",
                        event.getTaskId(),
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg());
            }
        } catch (Exception e) {
            log.warn("PushPlus AI 翻译任务通知发送异常: taskId={}, error={}", event.getTaskId(), e.getMessage());
        }
    }

    @Override
    public void sendServerIpSwitchNotification(ServerIpSwitchNotificationEvent event) {
        try {
            JSONObject config = getConfigValue(event.getCompanyId());
            if (!config.getBool("serverIpSwitchOpen", false)) {
                return;
            }
            String token = config.getStr("token");
            if (StrUtil.isBlank(token)) {
                return;
            }
            PushPlusSendResponse response = pushPlusClient.send(PushPlusSendRequest.builder()
                    .token(token)
                    .title(event.isRecovery() ? SERVER_RECOVERY_TITLE : SERVER_FAILOVER_TITLE)
                    .content(buildServerIpSwitchContent(event))
                    .channel(DEFAULT_CHANNEL)
                    .template(normalizeTemplate(config.getStr("template")))
                    .build());
            if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
                log.warn("PushPlus 服务器 IP 切换通知发送失败: serverName={}, switchType={}, code={}, msg={}",
                        event.getServerName(),
                        event.getSwitchType(),
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg());
            }
        } catch (Exception e) {
            log.warn("PushPlus 服务器 IP 切换通知发送异常: serverName={}, switchType={}, error={}",
                    event.getServerName(), event.getSwitchType(), e.getMessage());
        }
    }

    private JSONObject getConfigValue(Long companyId) {
        return dynamicConfigService.getConfigValue(CONFIG_NAME, null, companyId);
    }

    private String normalizeTemplate(String template) {
        String normalized = StrUtil.blankToDefault(template, DEFAULT_TEMPLATE).trim().toLowerCase();
        return SUPPORTED_TEMPLATES.contains(normalized) ? normalized : DEFAULT_TEMPLATE;
    }

    private String buildAiTranslateContent(AiTranslateTaskNotificationEvent event) {
        StringBuilder content = new StringBuilder();
        if (event.isRetry()) {
            appendLine(content, "重试人", event.getOperatorName());
        } else {
            appendLine(content, "提交人", event.getOperatorName());
        }
        appendLine(content, "商品", event.getProductTitle());
        appendLine(content, "目标国家", event.getCountryName());
        appendLine(content, "目标语言", event.getLanguageName());
        appendLine(content, "AI账号", event.getAiAccountName());
        if (event.isRetry()) {
            appendLine(content, "原任务ID", event.getOriginalTaskId());
            appendLine(content, "新任务ID", event.getTaskId());
        } else {
            appendLine(content, "任务ID", event.getTaskId());
        }
        appendLine(content, "提交时间", event.getCreatedAt() == null ? null : TIME_FORMATTER.format(event.getCreatedAt()));
        return content.toString();
    }

    private String buildServerIpSwitchContent(ServerIpSwitchNotificationEvent event) {
        StringBuilder content = new StringBuilder();
        appendLine(content, "切换类型", event.isRecovery() ? "恢复切回" : "故障切换");
        appendLine(content, "服务器", event.getServerName());
        appendLine(content, "CNAME", event.getCnameRecord());
        appendLine(content, "原IP", event.getFromIp());
        appendLine(content, "目标IP", event.getToIp());
        appendLine(content, "切换时间", event.getSwitchedAt() == null ? null : TIME_FORMATTER.format(event.getSwitchedAt()));
        return content.toString();
    }

    private void appendLine(StringBuilder content, String label, Object value) {
        content.append("- ")
                .append(label)
                .append("：")
                .append(value == null ? "-" : value)
                .append("\n");
    }
}
