package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeFailRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeHeartbeatResponse;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.task.provider.TurboFlowBridgeProvider;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@IgnoreResponsePackage
@RequestMapping("/turboflow-bridge")
public class TurboFlowBridgeController {

    private final TurboFlowBridgeProvider turboFlowBridgeProvider;

    @PostMapping("/tasks/poll")
    public TurboFlowBridgeTaskResponse poll(HttpServletRequest servletRequest,
                                            @RequestBody TurboFlowBridgePollRequest request) {
        try {
            return turboFlowBridgeProvider.pollTask(bearerToken(servletRequest), request);
        } catch (IllegalArgumentException e) {
            if (!isInvalidBridgeToken(e)) {
                throw e;
            }
            log.debug("[TurboFlowBridge] poll rejected: invalid bridge token, bridgeId={}",
                    request == null ? null : request.getBridgeId());
            return TurboFlowBridgeTaskResponse.builder()
                    .hasTask(false)
                    .message("invalid bridge token")
                    .build();
        }
    }

    @PostMapping("/tasks/complete")
    public TurboFlowBridgeHeartbeatResponse complete(HttpServletRequest servletRequest,
                                                     @RequestBody TurboFlowBridgeCompleteRequest request) {
        try {
            turboFlowBridgeProvider.completeTask(bearerToken(servletRequest), request);
            return TurboFlowBridgeHeartbeatResponse.builder().accepted(true).message("completed").build();
        } catch (IllegalArgumentException e) {
            if (!isInvalidBridgeToken(e)) {
                throw e;
            }
            log.debug("[TurboFlowBridge] complete rejected: invalid bridge token, assignmentId={}",
                    request == null ? null : request.getAssignmentId());
            return TurboFlowBridgeHeartbeatResponse.builder().accepted(false).message("invalid bridge token").build();
        }
    }

    @PostMapping("/tasks/fail")
    public TurboFlowBridgeHeartbeatResponse fail(HttpServletRequest servletRequest,
                                                 @RequestBody TurboFlowBridgeFailRequest request) {
        try {
            turboFlowBridgeProvider.failTask(bearerToken(servletRequest), request);
            return TurboFlowBridgeHeartbeatResponse.builder().accepted(true).message("failed").build();
        } catch (IllegalArgumentException e) {
            if (!isInvalidBridgeToken(e)) {
                throw e;
            }
            log.debug("[TurboFlowBridge] fail rejected: invalid bridge token, assignmentId={}",
                    request == null ? null : request.getAssignmentId());
            return TurboFlowBridgeHeartbeatResponse.builder().accepted(false).message("invalid bridge token").build();
        }
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private boolean isInvalidBridgeToken(IllegalArgumentException e) {
        return "missing bridge token".equals(e.getMessage())
                || "invalid TurboFlow bridge token".equals(e.getMessage());
    }
}
