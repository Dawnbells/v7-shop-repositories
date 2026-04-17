package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@Schema(description = "第三方网站信息响应")
public class ThirdPartyWebsiteResponse extends IdResponse {

    @Schema(title = "店铺名称")
    private String nickName;

    @Schema(title = "店铺唯一标识")
    private String handle;

    @Schema(title = "令牌")
    private String token;

    @Schema(title = "店铺认证状态")
    private ThirdPartyAuthStatusEnum authStatus;

    @Schema(title = "店铺类型")
    private WebsiteTypeEnum websiteType;

    @Schema(title = "归属人名字")
    private String ownerName;

    @Schema(title = "归属人部门")
    private String ownerDepartment;

    @Schema(title = "授权错误信息")
    private String authMessage;

    @Schema(title = "上次自动同步时间")
    private LocalDateTime lastSyncTime;

    @Schema(title = "上次手动同步时间")
    private LocalDateTime lastManualSyncTime;

    @Schema(title = "币种模式")
    private CurrencyMode currencyMode;

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
                .authStatus(entity.getAuthStatus())
                .websiteType(entity.getWebsiteType())
                .ownerName(ownerName)
                .ownerDepartment(ownerDepartment)
                .authMessage(entity.getAuthMessage())
                .lastSyncTime(entity.getLastSyncTime())
                .lastManualSyncTime(entity.getLastManualSyncTime())
                .currencyMode(entity.getCurrencyMode())
                .build();
    }
}
