package cn.v7soft.admin.controller.req;

import cn.hutool.json.JSONObject;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailSmtpTestRequest {

    @NotBlank(message = "测试收件人不能为空")
    @Email(message = "测试收件人邮箱格式不正确")
    private String recipient;

    @NotNull(message = "邮件发送配置不能为空")
    private JSONObject emailConfig;
}
