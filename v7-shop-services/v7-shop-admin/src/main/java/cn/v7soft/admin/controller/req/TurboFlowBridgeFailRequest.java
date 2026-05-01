package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurboFlowBridgeFailRequest {

    private String bridgeId;
    private String assignmentId;
    private String errorCode;
    private String message;
    private String stack;
    private Boolean retryable;
    private Long elapsedMs;
}
