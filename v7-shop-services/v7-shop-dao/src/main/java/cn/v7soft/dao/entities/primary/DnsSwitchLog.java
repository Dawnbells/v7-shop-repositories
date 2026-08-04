package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.enums.DnsSwitchEventType;
import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_dns_switch_logs")
public class DnsSwitchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "front_server_id")
    private Long frontServerId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "server_name", nullable = false, length = 100)
    private String serverName;

    @Column(name = "cname_record", length = 255)
    private String cnameRecord;

    @Column(name = "from_ip", nullable = false, length = 45)
    private String fromIp;

    @Column(name = "to_ip", nullable = false, length = 45)
    private String toIp;

    @Column(name = "switch_type", nullable = false, length = 20)
    private String switchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40)
    private DnsSwitchEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ip_role", length = 20)
    private FrontServerIpRole ipRole;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_health_status", length = 20)
    private FrontServerHealthStatus fromHealthStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_health_status", length = 20)
    private FrontServerHealthStatus toHealthStatus;

    @Column(name = "consecutive_count")
    private Integer consecutiveCount;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt;

    @Builder.Default
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;
}
