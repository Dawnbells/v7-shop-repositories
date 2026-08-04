package cn.v7soft.admin.event;

import java.time.LocalDateTime;

import cn.v7soft.dao.enums.DnsSwitchEventType;
import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServerHealthNotificationEvent {
    private final Long companyId;
    private final String serverName;
    private final String cnameRecord;
    private final DnsSwitchEventType eventType;
    private final FrontServerIpRole ipRole;
    private final String ipAddress;
    private final FrontServerHealthStatus fromHealthStatus;
    private final FrontServerHealthStatus toHealthStatus;
    private final String fromIp;
    private final String toIp;
    private final Integer consecutiveCount;
    private final String detail;
    private final LocalDateTime occurredAt;
}
