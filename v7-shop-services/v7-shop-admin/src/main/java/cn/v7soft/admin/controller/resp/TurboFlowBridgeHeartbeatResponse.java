package cn.v7soft.admin.controller.resp;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurboFlowBridgeHeartbeatResponse {

    private boolean accepted;
    private Long aiAccountId;
    private String message;
    /**
     * 机器可读的拒收原因，插件据此分流。
     * 目前只有 REPROCESS_REQUIRED：译图已收到但服务端后处理失败，assignment 已失效，
     * 插件应直接落盘译图等重派，不要再退避重投同一个 assignmentId。
     */
    private String reason;
}
