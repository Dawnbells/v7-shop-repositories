package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.converter.MapStringConverter;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.CertificateRequestStatus;
import cn.v7soft.dao.enums.CloakStrategy;
import cn.v7soft.dao.enums.DomainType;
import cn.v7soft.dao.enums.NginxConfigType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 一级域名实体类。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_top_level_domains")
public class TopLevelDomain extends BaseDataRangeEntity {
    /**
     * 一级域名名称
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 域名用途
     */
    @Enumerated(EnumType.STRING)
    private DomainType type;

    /**
     * 斗篷策略
     */
    @Enumerated(EnumType.STRING)
    private CloakStrategy cloakStrategy;
    /**
     * nginx配置类型
     */
    @Column(name = "nginx_config_type")
    @Enumerated(EnumType.STRING)
    private NginxConfigType nginxConfigType;

    /**
     * 域名的到期时间。
     */
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    /**
     * 关联的云平台账户
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cloud_platform_account_id")
    private CloudPlatformAccount cloudPlatformAccount;
    /**
     * 绑定的前端服务器
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_server_id", referencedColumnName = "id")
    private FrontServer frontServer;

    /**
     * SSL证书，嵌入式对象。
     */
    @Embedded
    private SSLCertificate sslCertificate;


    /**
     * 二级域名列表
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "parentDomain", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubDomain> subDomains;

    /**
     * 证书当前请求证书状态
     */
    @Enumerated(EnumType.STRING)
    private CertificateRequestStatus certificateRequestStatus;

    /**
     * 绑定协议
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", referencedColumnName = "id")
    private Protocol protocol;

    /**
     * 协议占位符值
     */
    @Column(name = "placeholder_values", columnDefinition = "JSON")
    @Convert(converter = MapStringConverter.class)
    private Map<String, String> placeholderValues;

    /**
     * 绑定的像素账号列表
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_top_level_domain_pixels",
            joinColumns = @JoinColumn(name = "top_level_domain_id"),
            inverseJoinColumns = @JoinColumn(name = "pixel_account_id")
    )
    private List<PixelAccount> pixelAccounts;
}
