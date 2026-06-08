package cn.v7soft.admin.service;

import cn.v7soft.admin.controller.req.SavePushPlusNotificationConfigRequest;
import cn.v7soft.admin.controller.resp.PushPlusNotificationConfigResponse;
import cn.v7soft.admin.controller.resp.PushPlusNotificationTestResponse;
import cn.v7soft.admin.event.AiTranslateTaskNotificationEvent;
import cn.v7soft.admin.event.ServerIpSwitchNotificationEvent;

public interface IPushPlusNotificationService {

    PushPlusNotificationConfigResponse getConfig();

    void saveConfig(SavePushPlusNotificationConfigRequest request);

    PushPlusNotificationTestResponse sendTest(String content);

    void sendAiTranslateTaskNotification(AiTranslateTaskNotificationEvent event);

    void sendServerIpSwitchNotification(ServerIpSwitchNotificationEvent event);
}
