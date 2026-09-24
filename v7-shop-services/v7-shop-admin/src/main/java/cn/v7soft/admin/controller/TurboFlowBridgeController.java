package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.v7soft.admin.controller.req.TurboFlowBridgeCompleteRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeFailRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgePollRequest;
import cn.v7soft.admin.controller.req.TurboFlowBridgeTranslatedRequest;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeHeartbeatResponse;
import cn.v7soft.admin.controller.resp.TurboFlowBridgeTaskResponse;
import cn.v7soft.admin.exception.TurboFlowCompletionInProgressException;
import cn.v7soft.admin.exception.TurboFlowReprocessRequiredException;
import cn.v7soft.admin.task.provider.TurboFlowBridgeProvider;
import cn.v7soft.admin.task.provider.TurboFlowUploadGate;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final TurboFlowUploadGate turboFlowUploadGate;

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

    @PostMapping("/tasks/translated")
    public TurboFlowBridgeHeartbeatResponse translated(HttpServletRequest servletRequest,
            @RequestBody TurboFlowBridgeTranslatedRequest request) {
        TurboFlowBridgeProvider.ReportState state =
                turboFlowBridgeProvider.translationReady(bearerToken(servletRequest), request);
        return TurboFlowBridgeHeartbeatResponse.builder().accepted(true)
                .status(state.name())
                .message(switch (state) {
                    case LEASE_EXTENDED -> "report lease extended";
                    case COMPLETING -> "previous upload is still being processed";
                    case COMPLETED -> "already completed";
                })
                .build();
    }

    /**
     * 上传大图前领取本 IP 的回传名额。BUSY 时插件退避后再领，GRANTED 后才发 /tasks/complete。
     * 请求体沿用 translated 通知的 {bridgeId, assignmentId}。
     */
    @PostMapping("/tasks/upload-slot")
    public TurboFlowBridgeHeartbeatResponse uploadSlot(HttpServletRequest servletRequest,
            @RequestBody TurboFlowBridgeTranslatedRequest request) {
        if (request == null || StrUtil.isBlank(request.getAssignmentId())) {
            throw new IllegalArgumentException("assignmentId is required");
        }
        String slotId = turboFlowUploadGate.tryAcquire(
                JakartaServletUtil.getClientIP(servletRequest), request.getAssignmentId());
        return TurboFlowBridgeHeartbeatResponse.builder().accepted(true)
                .status(slotId != null ? "GRANTED" : "BUSY")
                .uploadSlotId(slotId)
                .message(slotId != null ? "upload slot granted" : "too many uploads from this IP")
                .build();
    }

    @PostMapping("/tasks/complete")
    public ResponseEntity<TurboFlowBridgeHeartbeatResponse> complete(
            HttpServletRequest servletRequest,
            @RequestBody TurboFlowBridgeCompleteRequest request) {
        try {
            turboFlowBridgeProvider.completeTask(bearerToken(servletRequest), request);
            return ResponseEntity.ok(
                    TurboFlowBridgeHeartbeatResponse.builder().accepted(true).message("completed").build());
        } catch (TurboFlowReprocessRequiredException e) {
            // 译图收到了但服务端后处理失败，assignment 已失效、子任务已重排。
            // 必须回非 2xx：插件的 postJson 只在 !res.ok 时抛错，回 200 的话老插件会当上报成功、
            // 把译好的图直接丢掉。409 + body 里的 REPROCESS_REQUIRED 让新插件认出这个场景，
            // 跳过必然失败的三次退避、直接落盘译图，等新 assignmentId 重派时按 sha256 复用。
            log.warn("[TurboFlowBridge] complete needs reprocess, plugin should cache the translated image: assignmentId={}, cause={}",
                    request == null ? null : request.getAssignmentId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    TurboFlowBridgeHeartbeatResponse.builder()
                            .accepted(false)
                            .reason(TurboFlowReprocessRequiredException.REASON)
                            .message(e.getMessage())
                            .build());
        } catch (TurboFlowCompletionInProgressException e) {
            // 同一张图的上一份上传还在处理：不排队等锁，插件退避后发心跳确认结果
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    TurboFlowBridgeHeartbeatResponse.builder().accepted(false)
                            .reason(TurboFlowCompletionInProgressException.REASON).message(e.getMessage()).build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    TurboFlowBridgeHeartbeatResponse.builder().accepted(false)
                            .reason("COMPLETION_RETRY_REQUIRED").message(e.getMessage()).build());
        } catch (IllegalArgumentException e) {
            if (!isInvalidBridgeToken(e)) {
                throw e;
            }
            log.debug("[TurboFlowBridge] complete rejected: invalid bridge token, assignmentId={}",
                    request == null ? null : request.getAssignmentId());
            return ResponseEntity.ok(
                    TurboFlowBridgeHeartbeatResponse.builder().accepted(false).message("invalid bridge token").build());
        } finally {
            // 请求体已经传完，无论结果如何都把这次领到的名额让出来（只还自己那次，见 TurboFlowUploadGate）
            if (request != null) {
                turboFlowUploadGate.release(request.getAssignmentId(), request.getUploadSlotId());
            }
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
        } finally {
            // 回传重试耗尽后插件会上报 fail，此时它领过的名额不会再用
            turboFlowUploadGate.release(request == null ? null : request.getAssignmentId(), null);
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
