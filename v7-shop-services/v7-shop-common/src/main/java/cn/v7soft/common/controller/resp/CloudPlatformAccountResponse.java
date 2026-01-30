package cn.v7soft.common.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.CloudPlatformAccount;
import cn.v7soft.dao.enums.CloudPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "云平台账号信息响应")
public class CloudPlatformAccountResponse extends IdResponse {
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

    public static CloudPlatformAccountResponse convertEntity(CloudPlatformAccount entity) {
        CloudPlatformAccountResponseBuilder<?, ?> builder = CloudPlatformAccountResponse.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .accessKey(entity.getAccessKey())
                .accessKeySecret(entity.getAccessKeySecret())
                .endpoint(entity.getEndpoint());
        if (entity.getCloudPlatform() != null) {
            builder.cloudPlatform(entity.getCloudPlatform());
        }
        return filling(entity, builder.build());
    }
}
