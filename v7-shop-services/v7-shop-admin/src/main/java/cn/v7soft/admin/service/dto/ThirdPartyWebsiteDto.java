package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class ThirdPartyWebsiteDto extends IdDto {
    /**
     * 店铺名称
     */
    private String nickName;
    /**
     * 店铺的唯一标识
     */
    private String handle;
    /**
     * 第三方网站令牌
     */
    private String token;

    /**
     * 第三方网站的应用 Key
     */
    private String appKey;

    /**
     * 第三方网站的应用 Secret
     */
    private String appSecret;

    /**
     * 第三方商城授权状态
     */
    private ThirdPartyAuthStatusEnum authStatus;

    /**
     * 认证类型
     */
    private ThirdPartyAuthTypeEnum authType;

    /**
     * 第三方商城类型
     */
    private WebsiteTypeEnum websiteType;
    private SystemUserDto owner;
    public static ThirdPartyWebsiteDto convert(ThirdPartyWebsite thirdPartyWebsite) {
        return ThirdPartyWebsiteDto.builder()
                .id(String.valueOf(thirdPartyWebsite.getId()))
                .nickName(thirdPartyWebsite.getNickName())
                .handle(thirdPartyWebsite.getHandle())
                .token(thirdPartyWebsite.getToken())
                .appKey(thirdPartyWebsite.getAppKey())
                .appSecret(thirdPartyWebsite.getAppSecret())
                .authStatus(thirdPartyWebsite.getAuthStatus())
                .authType(thirdPartyWebsite.getAuthType())
                .websiteType(thirdPartyWebsite.getWebsiteType())
                .owner(SystemUserDto.convert(thirdPartyWebsite.getOwner()))
                .build();
    }
}
