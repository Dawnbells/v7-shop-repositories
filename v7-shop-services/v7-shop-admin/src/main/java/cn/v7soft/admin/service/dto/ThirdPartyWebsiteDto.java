package cn.v7soft.admin.service.dto;

import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

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
    private CurrencyMode currencyMode;
    private SystemUserDto owner;
    private String clientId;
    private String clientSecret;
    private LocalDateTime tokenExpiresAt;
    /**
     * Shopify 接入方式，null 视为 CREDENTIALS
     */
    private ShopifyAccessMode accessMode;
    private String refreshToken;
    private LocalDateTime refreshTokenExpiresAt;
    /**
     * 是否已获得 read_all_orders 权限（convert 时 null 归一为 false）
     */
    private Boolean hasAllOrdersScope;
    private LocalDateTime lastReconcileTime;
    private Long companyId;

    /**
     * 是否为 Shopify 公开应用接入（accessMode 为 null 时按凭证模式处理）
     */
    public boolean isPublicApp() {
        return ShopifyAccessMode.isPublicApp(accessMode);
    }

    public static ThirdPartyWebsiteDto convert(ThirdPartyWebsite thirdPartyWebsite) {
        return ThirdPartyWebsiteDto.builder()
                .id(String.valueOf(thirdPartyWebsite.getId()))
                .nickName(thirdPartyWebsite.getNickName())
                .handle(thirdPartyWebsite.getHandle())
                .token(thirdPartyWebsite.getToken())
                .authStatus(thirdPartyWebsite.getAuthStatus())
                .websiteType(thirdPartyWebsite.getWebsiteType())
                .lastSyncOrderId(thirdPartyWebsite.getLastSyncOrderId())
                .currencyMode(thirdPartyWebsite.getCurrencyMode())
                .owner(SystemUserDto.convert(thirdPartyWebsite.getOwner()))
                .clientId(thirdPartyWebsite.getClientId())
                .clientSecret(thirdPartyWebsite.getClientSecret())
                .tokenExpiresAt(thirdPartyWebsite.getTokenExpiresAt())
                .accessMode(thirdPartyWebsite.getAccessMode())
                .refreshToken(thirdPartyWebsite.getRefreshToken())
                .refreshTokenExpiresAt(thirdPartyWebsite.getRefreshTokenExpiresAt())
                .hasAllOrdersScope(Boolean.TRUE.equals(thirdPartyWebsite.getHasAllOrdersScope()))
                .lastReconcileTime(thirdPartyWebsite.getLastReconcileTime())
                .companyId(thirdPartyWebsite.getCompanyId())
                .build();
    }
}
