package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 前端服务器实体类，代表一个绑定域名和 IP 地址的前端服务器。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_front_servers")
@SQLRestriction("status <> 'DELETED'")
public class FrontServer extends BaseTenantEntity {

    /**
     * 服务器名称
     */
    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    /**
     * CNAME记录
     */
    @Column(name = "cname_record")
    private String cnameRecord;

    /**
     * 主IP 地址
     */
    @Column(name = "primary_ip", nullable = false, length = 45) // 45 是支持IPv6地址的长度
    private String primaryIp;

    /**
     * 故障转移 IP
     */
    @Column(name = "failover_ip", nullable = false, length = 45) // 45 是支持IPv6地址的长度
    private String failoverIp;

    /**
     * 健康检查地址 (例如 https://domain.com/health)
     */
    @Column(name = "health_check_url", nullable = false, length = 255)
    private String healthCheckUrl;

    /**
     * 是否已经发生过 IP 故障切换
     */
    @Column(name = "ip_switched", nullable = false)
    private boolean ipSwitched;

    /**
     * 解析次数
     */
    @Column(name = "resolution_count", nullable = false)
    private int resolutionCount;

    /**
     * 当前有效的域名解析数量
     */
    @Column(name = "active_resolution_count", nullable = false)
    private int activeResolutionCount;

    /**
     * 是否需要更新
     */
    @Column(name = "required_update")
    private boolean requiredUpdate;
}
