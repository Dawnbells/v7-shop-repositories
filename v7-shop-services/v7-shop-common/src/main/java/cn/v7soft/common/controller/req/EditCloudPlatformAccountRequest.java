package cn.v7soft.common.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.CloudPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditCloudPlatformAccountRequest extends IdRequest {
    @NotBlank(message = "名称不能为空")
    @Schema(title = "名称", example = "云平台账号")
    private String name;

    @Schema(title = "用途描述", example = "用于云存储")
    private String description;

    @Schema(title = "Access Key", example = "AKIA123456789")
    private String accessKey;

    @Schema(title = "Access Key Secret", example = "1234567890abcdef")
    private String accessKeySecret;

    @Schema(title = "接口访问端点", example = "https://cloud.example.com")
    private String endpoint;

    @Schema(title = "云平台", example = "AWS")
    private CloudPlatform cloudPlatform;
}
