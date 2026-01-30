package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import cn.v7soft.dao.enums.DomainType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * 二级域名实体类，代表一个二级域名。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_sub_domains", indexes = {
        @Index(name = "idx_sub_domain_full_name", columnList = "full_name"),
})
public class SubDomain extends BaseTenantEntity {
    /**
     * 二级域名名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 二级域名全称
     */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /**
     * 域名是否解析成功
     */
    @Column(name = "analyze_success")
    private boolean analyzeSuccess;

    /**
     * 域名用途
     */
    @Enumerated(EnumType.STRING)
    private DomainType type;

    /**
     * 跳转域名, 不为空的时候跳转
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "redirect_domain_id", referencedColumnName = "id")
    private SubDomain redirectDomain;

    /**
     * 绑定的前端服务器
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_server_id")
    private FrontServer frontServer;

    /**
     * 所属的一级域名
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_domain_id", referencedColumnName = "id")
    private TopLevelDomain parentDomain;

    /**
     * 所属的网站
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "website_id", referencedColumnName = "id")
    private Website website;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theme_id", referencedColumnName = "id")
    private ThemeCustom theme;

    /**
     * 绑定的像素账号列表
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_sub_domain_pixels",
            joinColumns = @JoinColumn(name = "sub_domain_id"),
            inverseJoinColumns = @JoinColumn(name = "pixel_account_id")
    )
    private List<PixelAccount> pixelAccounts;

    /**
     * 绑定的国家
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    /**
     * 缓存的货币（来自国家）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    /**
     * 缓存的语言（来自国家）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id")
    private Language language;

    /**
     * 绑定的SPU列表
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_sub_domain_spus",
            joinColumns = @JoinColumn(name = "sub_domain_id"),
            inverseJoinColumns = @JoinColumn(name = "spu_id")
    )
    private List<Spu> spuList;
}
