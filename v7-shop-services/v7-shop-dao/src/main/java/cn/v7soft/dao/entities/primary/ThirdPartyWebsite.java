package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.ThirdPartyAuthTypeEnum;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
@Table(name = "t_third_party_websites")
public class ThirdPartyWebsite extends BaseDataRangeEntity {
    /**
     * 店铺名称
     */
    @Column(name = "nick_name", nullable = false)
    private String nickName;
    /**
     * 店铺的唯一标识
     */
    @Column(name = "handle", nullable = false)
    private String handle;
    /**
     * 第三方网站令牌
     */
    @Column(name = "token", length = 1024)
    private String token;

    /**
     * 第三方网站的应用 Key
     */
    @Column(name = "app_key", nullable = false)
    private String appKey;

    /**
     * 第三方网站的应用 Secret
     */
    @Column(name = "app_secret", nullable = false)
    private String appSecret;

    /**
     * 第三方商城授权状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_status", nullable = false, length = 50)
    private ThirdPartyAuthStatusEnum authStatus;

    /**
     * 认证类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 50)
    private ThirdPartyAuthTypeEnum authType;

    /**
     * 第三方商城类型
     */
    @Column(name = "website_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private WebsiteTypeEnum websiteType;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /**
     * 上次自动同步拉取到的最后一条 Shopline 订单 ID，用于 since_id 去重
     */
    @Column(name = "last_sync_order_id")
    private String lastSyncOrderId;

    @Column(name = "auth_message", length = 500)
    private String authMessage;

    @Column(name = "last_manual_sync_time")
    private LocalDateTime lastManualSyncTime;
}
