package cn.v7soft.admin.controller.req;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurboFlowBridgeHeartbeatRequest {

    private String bridgeId;
    private String version;
    private Boolean flowConnected;
    private String projectId;
    private String currentUrl;
    private Boolean busy;
    private Map<String, Object> accountInfo;
}
