package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class ThirdPartyWebsiteDto extends IdDto {
    private String nickName;
    private String handle;
    private String token;
    private ThirdPartyAuthStatusEnum authStatus;
    private WebsiteTypeEnum websiteType;
    private String lastSyncOrderId;
    private SystemUserDto owner;

    public static ThirdPartyWebsiteDto convert(ThirdPartyWebsite thirdPartyWebsite) {
        return ThirdPartyWebsiteDto.builder()
                .id(String.valueOf(thirdPartyWebsite.getId()))
                .nickName(thirdPartyWebsite.getNickName())
                .handle(thirdPartyWebsite.getHandle())
                .token(thirdPartyWebsite.getToken())
                .authStatus(thirdPartyWebsite.getAuthStatus())
                .websiteType(thirdPartyWebsite.getWebsiteType())
                .lastSyncOrderId(thirdPartyWebsite.getLastSyncOrderId())
                .owner(SystemUserDto.convert(thirdPartyWebsite.getOwner()))
                .build();
    }
}
