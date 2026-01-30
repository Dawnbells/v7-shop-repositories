package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "第三方网站信息响应")
public class ThirdPartyWebsiteResponse extends IdResponse {

    @Schema(title = "店铺名称", example = "example-token")
    private String nickName;

    @Schema(title = "店铺唯一标识", example = "example-token")
    private String handle;

    @Schema(title = "令牌", example = "example-token")
    private String token;

    @Schema(title = "应用 Key", example = "example-app-key")
    private String appKey;

    @Schema(title = "应用 Secret", example = "example-app-secret")
    private String appSecret;

    @Schema(title = "店铺认证状态", example = "AUTHED")
    private ThirdPartyAuthStatusEnum authStatus;

    @Schema(title = "认证类型", example = "OAUTH2")
    private ThirdPartyAuthTypeEnum authType;

    @Schema(title = "店铺类型", example = "SHOPLINE")
    private WebsiteTypeEnum websiteType;

    @Schema(title = "归属人名字", example = "张三")
    private String ownerName;

    @Schema(title = "归属人部门", example = "COD一部")
    private String ownerDepartment;

    /**
     * 从实体转换为响应对象
     */
    public static ThirdPartyWebsiteResponse convertEntity(ThirdPartyWebsite entity) {
        SystemUser owner = entity.getOwner();
        String ownerName = "";
        String ownerDepartment = "";
        if (owner != null) {
            ownerName = owner.getName();
            if (owner.getDepartment() != null) {
                ownerDepartment = owner.getDepartment().getName();
            }
        }
        return ThirdPartyWebsiteResponse.builder()
                .id(String.valueOf(entity.getId()))
                .nickName(entity.getNickName())
                .handle(entity.getHandle())
                .token(entity.getToken())
                .appKey(entity.getAppKey())
                .appSecret(entity.getAppSecret())
                .authType(entity.getAuthType())
                .authStatus(entity.getAuthStatus())
                .websiteType(entity.getWebsiteType())
                .ownerName(ownerName)
                .ownerDepartment(ownerDepartment)
                .build();
    }
}
