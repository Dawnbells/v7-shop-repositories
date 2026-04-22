package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.converter.MapStringConverter;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.LandingPageType;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 子域名SPU落地页配置实体类
 * 存储子域名、SPU的落地页配置关系
 * 通过 LAND 类型记录表示绑定关系，同时存储主题配置、站点配置、变量等
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_sub_domain_spu_landing_pages")
@IdClass(SubDomainSpuLandingPageId.class)
public class SubDomainSpuLandingPage {

    @Id
    @Column(name = "sub_domain_id")
    private Long subDomainId;

    @Id
    @Column(name = "spu_id")
    private Long spuId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "landing_page_type")
    private LandingPageType landingPageType;

    /**
     * 实际显示的落地页 SPU ID
     * LAND 类型时等于 spuId，CLOAK/BLACKLISTED 类型可配置不同的 SPU
     */
    @Column(name = "landing_spu_id")
    private Long landingSpuId;

    /**
     * 主题配置（页面布局、组件、样式）
     */
    @Column(name = "theme_config", columnDefinition = "JSON")
    private String themeConfig;

    /**
     * 变量定义结构（仅编辑器使用）
     */
    @Column(name = "variable_schema", columnDefinition = "JSON")
    private String variableSchema;

    /**
     * 站点配置值
     */
    @Column(name = "site_config", columnDefinition = "JSON")
    private String siteConfig;

    /**
     * 变量实际值
     */
    @Column(name = "variable_values", columnDefinition = "JSON")
    private String variableValues;

    /**
     * 绑定的协议
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id")
    private Protocol protocol;

    /**
     * 协议占位符值
     */
    @Column(name = "protocol_placeholder_values", columnDefinition = "JSON")
    @Convert(converter = MapStringConverter.class)
    private Map<String, String> protocolPlaceholderValues;

    /**
     * 广告平台（仅 LAND 类型使用）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ad_platform", length = 32)
    private PixelAccountPlatform adPlatform;

    /**
     * 流量媒介（仅 LAND 类型使用）
     */
    @Column(name = "medium", length = 32)
    private String medium;

    /**
     * 斗篷策略（仅 LAND 类型使用）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cloak_strategy", length = 32)
    private CloakStrategy cloakStrategy;

    /**
     * 推广活动标识，仅英文/数字/下划线（仅 LAND 类型使用）
     */
    @Column(name = "campaign", length = 128)
    private String campaign;

    /**
     * Campaign 保存日期（YYYYMM，仅 LAND 类型使用）
     */
    @Column(name = "campaign_date", length = 6)
    private String campaignDate;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_domain_id", insertable = false, updatable = false)
    private SubDomain subDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spu_id", insertable = false, updatable = false)
    private Spu spu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landing_spu_id", insertable = false, updatable = false)
    private Spu landingSpu;
}
