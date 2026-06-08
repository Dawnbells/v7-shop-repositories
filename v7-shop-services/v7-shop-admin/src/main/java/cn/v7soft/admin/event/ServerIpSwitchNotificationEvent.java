package cn.v7soft.admin.event;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class ServerIpSwitchNotificationEvent {

    private final Long companyId;
    private final String serverName;
    private final String cnameRecord;
    private final String fromIp;
    private final String toIp;
    private final String switchType;
    private final LocalDateTime switchedAt;

    private ServerIpSwitchNotificationEvent(Long companyId,
                                            String serverName,
                                            String cnameRecord,
                                            String fromIp,
                                            String toIp,
                                            String switchType,
                                            LocalDateTime switchedAt) {
        this.companyId = companyId;
        this.serverName = serverName;
        this.cnameRecord = cnameRecord;
        this.fromIp = fromIp;
        this.toIp = toIp;
        this.switchType = switchType;
        this.switchedAt = switchedAt;
    }

    public static ServerIpSwitchNotificationEvent of(Long companyId,
                                                     String serverName,
                                                     String cnameRecord,
                                                     String fromIp,
                                                     String toIp,
                                                     String switchType,
                                                     LocalDateTime switchedAt) {
        return new ServerIpSwitchNotificationEvent(companyId, serverName, cnameRecord, fromIp, toIp, switchType, switchedAt);
    }

    public boolean isRecovery() {
        return "RECOVERY".equalsIgnoreCase(switchType);
    }
}
