package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.CloudPlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 第三方云平台账号实体类。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "t_cloud_platform_accounts")
@SQLRestriction("status <> 'DELETED'")
public class CloudPlatformAccount extends BaseDataRangeEntity {
    /**
     * 名称
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 用途描述说明
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 云平台账户 AccessKey
     */
    @Column(name = "access_key", nullable = false, length = 100)
    private String accessKey;

    /**
     * 云平台账户 AccessKey Secret
     */
    @Column(name = "access_key_secret", nullable = false, length = 100)
    private String accessKeySecret;

    /**
     * 云平台账户接口访问端点
     */
    @Column(name = "endpoint", length = 255)
    private String endpoint;

    /**
     * 是否使用 Gcore 的 DNS 解析服务。
     */
    @Column(name = "dns_gcore")
    private Boolean dnsGcore;

    /**
     * gcore api token
     */
    @Column(name = "gcore_api_token", length = 255)
    private String gcoreApiToken;

    /**
     * 所属云平台。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_platform", nullable = false, length = 20)
    private CloudPlatform cloudPlatform;
}
