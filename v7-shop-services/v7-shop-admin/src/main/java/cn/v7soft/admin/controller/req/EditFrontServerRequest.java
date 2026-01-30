package cn.v7soft.admin.controller.req;


import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditFrontServerRequest extends IdRequest {

    @NotBlank(message = "服务器名称不能为空")
    @Schema(title = "服务器名称", example = "Server-1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Pattern(regexp = "^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,6}$", message = "绑定的CNAME域名不正确")
    @Schema(title = "CNAME记录", example = "eu.dwd-cname.com", description = "绑定的CNAME域名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cnameRecord;

    @NotBlank(message = "主IP地址不能为空")
    @Schema(title = "主IP地址", example = "192.168.1.1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String primaryIp;

    @Schema(title = "故障转移IP地址", example = "192.168.1.2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String failoverIp;

    @NotBlank(message = "健康检查地址不能为空")
    @Schema(title = "健康检查地址", example = "https://domain.com/health", requiredMode = Schema.RequiredMode.REQUIRED)
    private String healthCheckUrl;
}
