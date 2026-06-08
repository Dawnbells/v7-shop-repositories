package cn.v7soft.admin.controller.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushPlusNotificationTestRequest {

    @NotBlank(message = "测试内容不能为空")
    private String content;
}
