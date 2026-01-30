package cn.v7soft.accountservice.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(title = "登录请求", description = "用于接收用户登录请求的数据传输对象")
public class LoginRequest {

    @Schema(title = "手机号", example = "15880411714", requiredMode = Schema.RequiredMode.REQUIRED)
    private String telephone;

    @Schema(title = "密码", example = "Abc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
