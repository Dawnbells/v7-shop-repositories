package cn.v7soft.dao.entities.primary;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.enums.BrowserPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 订单风险相关信息
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_temporary_risk_record_infos", indexes = {
        @Index(name = "idx_device_id", columnList = "device_id"),
        @Index(name = "idx_remote_ip", columnList = "remote_ip"),
        @Index(name = "idx_real_ip", columnList = "real_ip"),
})
public class TemporaryOrderRiskRecordInfo extends BaseAutoIdDataRangeEntity {
    /**
     * 设备ID
     */
    @Column(name = "device_id", length = 32)
    private String deviceId;
    /**
     * 远程IP地址
     */
    @Column(name = "remote_ip", length = 32)
    private String remoteIp;
    /**
     * 远程IP地址信息
     */
    @Column(name = "remote_ip_info", length = 512)
    private String remoteIpInfo;

    /**
     * 真实IP地址
     */
    @Column(name = "real_ip", length = 32)
    private String realIp;

    /**
     * 真实IP地址信息
     */
    @Column(name = "real_ip_info", length = 512)
    private String realIpInfo;

    /**
     * UA信息
     */
    @Column(name = "ua", length = 512)
    private String ua;

    /**
     * key
     */
    @Column(name = "pd_key")
    private String pdKey;
    /**
     * pd value
     */
    @Column(name = "pd_val")
    private String pdVal;

    /**
     * 下单页面类型
     */
    @Column(name = "cloak")
    private Boolean cloak;

    /**
     * 下单平台
     */
    @Column(name = "browser_platform")
    @Enumerated(EnumType.STRING)
    private BrowserPlatform browserPlatform;
}
