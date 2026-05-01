package cn.v7soft.admin.controller.resp;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurboFlowBridgeHeartbeatResponse {

    private boolean accepted;
    private Long aiAccountId;
    private String message;
}
