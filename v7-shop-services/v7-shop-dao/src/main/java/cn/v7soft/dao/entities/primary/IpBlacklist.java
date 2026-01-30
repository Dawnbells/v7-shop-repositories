package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * IP黑名单
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_ip_blacklist")
public class IpBlacklist extends BaseDataRangeEntity {

    /**
     * 被拉黑的IP地址
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * 浏览器指纹
     */
    @Column(name = "fingerprint")
    private String fingerprint;
    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;
}
