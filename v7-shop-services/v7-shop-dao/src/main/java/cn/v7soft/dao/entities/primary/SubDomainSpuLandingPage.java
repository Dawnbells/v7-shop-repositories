package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.enums.LandingPageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
     * 落地页SPU ID（CLOAK/BLACKLISTED类型使用，关联到实际展示的产品）
     */
    @Column(name = "landing_page_spu_id")
    private Long landingPageSpuId;

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
    @JoinColumn(name = "landing_page_spu_id", insertable = false, updatable = false)
    private Spu landingPageSpu;
}
