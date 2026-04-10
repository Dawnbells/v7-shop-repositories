package cn.v7soft.dao.entities.primary;

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

    @Column(name = "server_name", nullable = false, length = 100)
    private String serverName;

    @Column(name = "from_ip", nullable = false, length = 45)
    private String fromIp;

    @Column(name = "to_ip", nullable = false, length = 45)
    private String toIp;

    @Column(name = "switch_type", nullable = false, length = 20)
    private String switchType;

    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt;

    @Builder.Default
    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;
}
