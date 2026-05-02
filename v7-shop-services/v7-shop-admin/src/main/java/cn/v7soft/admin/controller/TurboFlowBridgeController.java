package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeFailRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeHeartbeatResponse;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.task.provider.TurboFlowBridgeProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/turboflow-bridge")
public class TurboFlowBridgeController {

    private final TurboFlowBridgeProvider turboFlowBridgeProvider;

    @PostMapping("/tasks/poll")
    public TurboFlowBridgeTaskResponse poll(HttpServletRequest servletRequest,
                                            @RequestBody TurboFlowBridgePollRequest request) {
        return turboFlowBridgeProvider.pollTask(bearerToken(servletRequest), request);
    }

    @PostMapping("/tasks/complete")
    public TurboFlowBridgeHeartbeatResponse complete(HttpServletRequest servletRequest,
                                                     @RequestBody TurboFlowBridgeCompleteRequest request) {
        turboFlowBridgeProvider.completeTask(bearerToken(servletRequest), request);
        return TurboFlowBridgeHeartbeatResponse.builder().accepted(true).message("completed").build();
    }

    @PostMapping("/tasks/fail")
    public TurboFlowBridgeHeartbeatResponse fail(HttpServletRequest servletRequest,
                                                 @RequestBody TurboFlowBridgeFailRequest request) {
        turboFlowBridgeProvider.failTask(bearerToken(servletRequest), request);
        return TurboFlowBridgeHeartbeatResponse.builder().accepted(true).message("failed").build();
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
