package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.CurrencyMode;
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
}
