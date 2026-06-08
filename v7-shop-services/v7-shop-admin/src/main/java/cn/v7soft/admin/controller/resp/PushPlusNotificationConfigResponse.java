package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushPlusNotificationConfigResponse {

    private Boolean open;

    private Boolean tokenSet;

    private String template;
}
