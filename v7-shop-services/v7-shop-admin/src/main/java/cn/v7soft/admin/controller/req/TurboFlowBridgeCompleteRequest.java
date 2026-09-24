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
    /** /tasks/upload-slot 发的名额 id，处理结束后据此归还；老插件不带。 */
    private String uploadSlotId;
}
