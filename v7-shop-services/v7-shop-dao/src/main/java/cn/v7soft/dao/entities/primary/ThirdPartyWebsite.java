package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 第三方网站实体类，代表第三方认证信息。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_third_party_websites", indexes = {
        @Index(name = "uk_handle", columnList = "handle", unique = true),
        @Index(name = "uk_token", columnList = "token", unique = true)
})
public class ThirdPartyWebsite extends BaseDataRangeEntity {
    /**
     * 店铺名称
     */
    @Column(name = "nick_name", nullable = false)
    private String nickName;
    /**
     * 店铺的唯一标识
     */
    @Column(name = "handle", nullable = false, unique = true)
    private String handle;
    /**
     * 第三方网站令牌
     */
    @Column(name = "token", nullable = false, length = 1024, unique = true)
    private String token;

    /**
     * 第三方商城授权状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_status", nullable = false, length = 50)
    private ThirdPartyAuthStatusEnum authStatus;

    /**
     * 第三方商城类型
     */
    @Column(name = "website_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private WebsiteTypeEnum websiteType;

    /**
     * 上次发起自动同步的服务器时间
     */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 上次自动同步拉取到的最后一条 Shopline 订单 ID，用于 since_id 去重
     */
    @Column(name = "last_sync_order_id")
    private String lastSyncOrderId;

    /**
     * 上次自动同步拉取到的最后一条订单的创建时间，用于 created_at_min
     */
    @Column(name = "last_sync_order_time")
    private LocalDateTime lastSyncOrderTime;

    /**
     * 上次自动同步是否有新增订单
     */
    @Column(name = "last_sync_has_new_orders")
    @Builder.Default
    private Boolean lastSyncHasNewOrders = false;

    /**
     * 订单同步使用的币种模式
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency_mode", nullable = false, length = 30)
    @Builder.Default
    private CurrencyMode currencyMode = CurrencyMode.SHOP_MONEY;

    @Column(name = "auth_message", length = 500)
    private String authMessage;

    @Column(name = "last_manual_sync_time")
    private LocalDateTime lastManualSyncTime;

    /**
     * 上次检查已同步订单取消状态时处理到的订单更新时间，用于 updated_at_min
     */
    @Column(name = "last_cancel_check_time")
    private LocalDateTime lastCancelCheckTime;

    /** 取消检查达到单轮页数上限时保留 Shopify page_info，重启后也可续查。 */
    @Column(name = "cancel_check_page_info", columnDefinition = "TEXT")
    private String cancelCheckPageInfo;

    /**
     * Shopify 应用的 Client ID（仅 SHOPIFY 类型使用）
     */
    @Column(name = "client_id")
    private String clientId;

    /**
     * Shopify 应用的 Client Secret（仅 SHOPIFY 类型使用）
     */
    @Column(name = "client_secret", length = 512)
    private String clientSecret;

    /**
     * token 的过期时间（仅 SHOPIFY 类型使用，token 由 Client ID/Secret 换取，24 小时有效）
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * Shopify 接入方式（仅 SHOPIFY 类型使用），null 视为 CREDENTIALS（存量记录）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", length = 30)
    private ShopifyAccessMode accessMode;

    /**
     * 公开应用的 refresh token（轮换，每次刷新后更新）
     */
    @Column(name = "refresh_token", length = 1024)
    private String refreshToken;

    /**
     * refresh token 过期时间
     */
    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    /**
     * OAuth 回调或刷新返回的 scope 原文
     */
    @Column(name = "granted_scopes", length = 512)
    private String grantedScopes;

    /**
     * 是否已获得 read_all_orders 权限（否则只能读取最近 60 天订单），null 视为 false
     */
    @Column(name = "has_all_orders_scope")
    @Builder.Default
    private Boolean hasAllOrdersScope = false;

    /**
     * 最近一次公开应用授权成功时间
     */
    @Column(name = "installed_at")
    private LocalDateTime installedAt;

    /**
     * 最近一次收到 app/uninstalled 的时间
     */
    @Column(name = "uninstalled_at")
    private LocalDateTime uninstalledAt;

    /**
     * 公开应用对账游标：已处理到的 Shopify 订单 updated_at（东八区，与 lastCancelCheckTime 同约定）
     */
    @Column(name = "last_reconcile_time")
    private LocalDateTime lastReconcileTime;
}
