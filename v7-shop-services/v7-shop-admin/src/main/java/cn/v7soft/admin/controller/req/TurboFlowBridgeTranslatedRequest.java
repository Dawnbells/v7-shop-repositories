package cn.v7soft.admin.controller.req;

import lombok.Getter;
import lombok.Setter;

/** Small notification sent before uploading a completed translation. */
@Getter
@Setter
public class TurboFlowBridgeTranslatedRequest {
    private String bridgeId;
    private String assignmentId;
}
