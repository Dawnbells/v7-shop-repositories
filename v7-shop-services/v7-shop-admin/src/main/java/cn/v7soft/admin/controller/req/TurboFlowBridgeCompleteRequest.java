package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurboFlowBridgeCompleteRequest {

    private String bridgeId;
    private String assignmentId;
    private String resultImageBase64;
    private String resultMimeType;
    private String resultUrl;
    private Long elapsedMs;
    private String imageHash;
    private Boolean policyFallback;
    private String policyFallbackStatus;
    private String policyFallbackReason;
}
