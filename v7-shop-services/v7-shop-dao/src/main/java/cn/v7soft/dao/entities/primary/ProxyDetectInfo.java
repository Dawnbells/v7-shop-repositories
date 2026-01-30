package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 订单风险相关信息
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_proxy_detect_info", indexes = {
        @Index(name = "idx_key_val", columnList = "pd_key, pd_val")
})
public class ProxyDetectInfo extends BaseEntity {
    /**
     * 是否是代理
     */
    @Column(name = "is_proxy")
    private boolean isProxy;
    /**
     * 置信度
     */
    @Column(name = "confidence")
    private long confidence;
    /**
     * 描述
     */
    @Column(name = "message")
    private String message;
    /**
     * 用时
     */
    @Column(name = "elapsed_ms")
    private long elapsedMs;
    /**
     * key
     */
    @Column(name = "pd_key")
    private String pdKey;
    /**
     * uuid
     */
    @Column(name = "pd_val")
    private String pdVal;
    /**
     * 2位国家代码
     */
    @Column(name = "country_code")
    private String countryCode;
    /**
     * 大洲代码
     */
    @Column(name = "continent_code")
    private String continentCode;
    /**
     * 爬虫
     */
    @Column(name = "crawler")
    private String crawler;

    /**
     * 远程IP地址
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "remote_ip_id")
    private IpDetailInfo remoteIp;

    /**
     * 真实IP地址
     */
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "real_ip_id")
    private IpDetailInfo realIp;
}
