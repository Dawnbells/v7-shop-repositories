package cn.v7soft.admin.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import cn.v7soft.admin.task.HealthCheckTask.NodeRuntimeState;
import cn.v7soft.dao.enums.FrontServerIpRole;

/**
 * 探测失败日志的去重策略：首次必打、周期内静默、原因变化立即打、恢复留痕。
 * 目的是防止「确认故障后持续失败」退化成每轮一条刷屏，或反过来彻底静默。
 */
class NodeFailureLogThrottleTest {

    private static final int HEARTBEAT_ROUNDS = 3;

    @Test
    void logsFirstFailureThenStaysQuietUntilPeriodElapses() {
        NodeRuntimeState node = new NodeRuntimeState(FrontServerIpRole.PRIMARY, "10.0.0.1");

        assertTrue(probeFails(node, "HTTP 301"), "首次失败必须打印");
        assertFalse(probeFails(node, "HTTP 301"), "周期内相同原因不再打印");
        assertFalse(probeFails(node, "HTTP 301"), "周期内相同原因不再打印");
        assertTrue(probeFails(node, "HTTP 301"), "满一个周期后再打印一次");
        assertFalse(probeFails(node, "HTTP 301"), "打印后重新进入静默");
    }

    @Test
    void logsImmediatelyWhenFailureReasonChanges() {
        NodeRuntimeState node = new NodeRuntimeState(FrontServerIpRole.FAILOVER, "10.0.0.2");

        assertTrue(probeFails(node, "HTTP 301"));
        assertFalse(probeFails(node, "HTTP 301"));
        assertTrue(probeFails(node, "ConnectException: 拒绝连接"), "失败原因变化应立即打印");
    }

    @Test
    void logsRecoveryOnlyOnceAndResetsThrottle() {
        NodeRuntimeState node = new NodeRuntimeState(FrontServerIpRole.FALLBACK, "10.0.0.3");

        assertFalse(node.consumeRecoveryFlag(), "没失败过就不该打恢复日志");

        probeFails(node, "SocketTimeoutException");
        assertTrue(node.consumeRecoveryFlag(), "失败后首次成功应打印恢复日志");
        assertFalse(node.consumeRecoveryFlag(), "恢复日志只打一次");

        assertTrue(probeFails(node, "SocketTimeoutException"), "恢复后再次失败视为首次，立即打印");
    }

    /** 模拟真实调用顺序：先判断是否打印日志，再把结果记入连续计数 */
    private boolean probeFails(NodeRuntimeState node, String detail) {
        boolean shouldLog = node.shouldLogFailure(detail, HEARTBEAT_ROUNDS);
        node.record(HealthProbeResult.unhealthy(detail));
        return shouldLog;
    }
}
