package cn.v7soft.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.v7soft.admin.controller.req.PushPlusNotificationTestRequest;
import cn.v7soft.admin.controller.req.SavePushPlusNotificationConfigRequest;
import cn.v7soft.admin.controller.resp.PushPlusNotificationConfigResponse;
import cn.v7soft.admin.controller.resp.PushPlusNotificationTestResponse;
import cn.v7soft.admin.service.IPushPlusNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pushplus-notification")
@Tag(name = "PushPlus 微信通知")
@RequiredArgsConstructor
public class PushPlusNotificationController {

    private final IPushPlusNotificationService pushPlusNotificationService;

    @SaCheckLogin
    @GetMapping("/config")
    @Operation(summary = "获取 PushPlus 微信通知配置")
    public PushPlusNotificationConfigResponse getConfig() {
        return pushPlusNotificationService.getConfig();
    }

    @SaCheckLogin
    @PostMapping("/config")
    @Operation(summary = "保存 PushPlus 微信通知配置")
    public void saveConfig(@Valid @RequestBody SavePushPlusNotificationConfigRequest request) {
        pushPlusNotificationService.saveConfig(request);
    }

    @SaCheckLogin
    @PostMapping("/test-send")
    @Operation(summary = "发送 PushPlus 微信测试通知")
    public PushPlusNotificationTestResponse testSend(@Valid @RequestBody PushPlusNotificationTestRequest request) {
        return pushPlusNotificationService.sendTest(request.getContent());
    }
}
