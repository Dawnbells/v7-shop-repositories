package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavePushPlusNotificationConfigRequest {

    private Boolean open;

    private String token;

    private String template;
}
