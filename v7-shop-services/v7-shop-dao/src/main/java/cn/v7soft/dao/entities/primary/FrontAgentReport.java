package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 前端机 agent 同步回报记录（全局表，不分租户）。
 * <p>
 * agent 每次轮询 manifest 时通过查询参数上报「已应用版本 + 应用结果」（轮询即回报），
 * 按 agentName 一行 upsert。取代旧的 {@code FrontServer.requiredUpdate} 标志：
 * 后台据此判断每台前端机的配置是否生效；reportedAt 停滞（如 >5 分钟未上报）
 * 即代表 agent 失联/断网/鉴权失败，应标红告警。
 * <p>
 * 设计文档：docs/superpowers/specs/2026-06-12-nginx-config-refactor-design.md §4.4
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_front_agent_report", uniqueConstraints = @UniqueConstraint(columnNames = "agent_name"))
public class FrontAgentReport extends BaseEntity {

    /**
     * 前端机唯一标识（前端机 .env 的 FRONT_SERVER_NAME，轮询参数 ?agent=）
     */
    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    /**
     * 该前端机最近一次成功应用的 manifest 版本号（形如 sha256:...）
     */
    @Column(name = "applied_version", length = 80)
    private String appliedVersion;

    /**
     * 最近一次上报的结果：ok / error
     */
    @Column(name = "report_status", length = 16)
    private String reportStatus;

    /**
     * 上报的错误摘要（reportStatus=error 时）
     */
    @Column(name = "message", length = 1000)
    private String message;

    /**
     * 最近一次上报时间（心跳；停滞代表 agent 失联）
     */
    @Column(name = "reported_at")
    private LocalDateTime reportedAt;
}
