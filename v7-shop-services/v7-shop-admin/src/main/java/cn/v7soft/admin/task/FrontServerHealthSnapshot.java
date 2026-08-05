package cn.v7soft.admin.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;

/**
 * 健康检查的只读快照。
 *
 * <p>{@link HealthCheckTask} 的判定结果原本只存在于任务 bean 的内存里，外部拿不到。这里由任务
 * 每轮产出一份不可变快照交给 {@link FrontServerHealthSnapshotHolder}，读方只看快照、不碰任务的
 * 可变状态，两边都不需要加锁。
 *
 * @param enabled   健康检查是否启用；dev profile 下任务整个不运行，此处为 false
 * @param updatedAt 快照产出时间；null 表示任务已启用但还没跑完第一轮
 * @param servers   参与检查的服务器；任务在跑但一台都没有时为空列表
 */
public record FrontServerHealthSnapshot(
        boolean enabled,
        LocalDateTime updatedAt,
        List<ServerHealth> servers) {

    private static final FrontServerHealthSnapshot DISABLED =
            new FrontServerHealthSnapshot(false, null, List.of());
    private static final FrontServerHealthSnapshot AWAITING_FIRST_ROUND =
            new FrontServerHealthSnapshot(true, null, List.of());

    /** dev profile 专用：让前端显示「未启用」，而不是把一片 UNKNOWN 误读成全部故障 */
    public static FrontServerHealthSnapshot disabled() {
        return DISABLED;
    }

    /** 任务已启用但首轮未完成，与「跑完了但一台服务器都没有」区分开 */
    public static FrontServerHealthSnapshot awaitingFirstRound() {
        return AWAITING_FIRST_ROUND;
    }

    public static FrontServerHealthSnapshot of(LocalDateTime updatedAt, List<ServerHealth> servers) {
        return new FrontServerHealthSnapshot(true, updatedAt, List.copyOf(servers));
    }

    /**
     * 单台服务器的快照。
     *
     * @param dnsIp DNS 回查到的实际解析 IP；回查失败或尚未回查时为 null，此时不会有任何节点被标记为生效
     * @param nodes 恒定三条、恒定顺序（主 / 备用 / 兜底），未配置的角色也占位
     */
    public record ServerHealth(String serverName, String dnsIp, List<NodeHealth> nodes) {

        public ServerHealth {
            nodes = List.copyOf(nodes);
        }

        /**
         * 按 {@link FrontServerIpRole} 的声明顺序补齐三条节点。未配置的角色补一条占位记录，
         * 这样前端「从左到右是主/备/兜底」的位置约定才不会因为少配一个 IP 而错位。
         */
        static ServerHealth build(
                String serverName, String dnsIp, Function<FrontServerIpRole, NodeHealth> lookup) {
            List<NodeHealth> nodes = new ArrayList<>(FrontServerIpRole.values().length);
            for (FrontServerIpRole role : FrontServerIpRole.values()) {
                NodeHealth node = lookup.apply(role);
                nodes.add(node != null ? node : NodeHealth.unconfigured(role));
            }
            return new ServerHealth(serverName, dnsIp, nodes);
        }
    }

    /**
     * @param configured 该角色是否配置了 IP；未配置时 ip 为 null、status 恒为 UNKNOWN
     * @param active     DNS 当前是否正指向该节点
     */
    public record NodeHealth(
            FrontServerIpRole role,
            String ip,
            FrontServerHealthStatus status,
            boolean configured,
            boolean active) {

        static NodeHealth unconfigured(FrontServerIpRole role) {
            return new NodeHealth(role, null, FrontServerHealthStatus.UNKNOWN, false, false);
        }
    }
}
