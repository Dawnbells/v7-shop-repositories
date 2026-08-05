package cn.v7soft.admin.controller.resp;

import java.time.LocalDateTime;
import java.util.List;

import cn.v7soft.admin.task.FrontServerHealthSnapshot;
import cn.v7soft.dao.enums.FrontServerHealthStatus;
import cn.v7soft.dao.enums.FrontServerIpRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "前端服务器主备兜底IP健康状态")
public class FrontServerHealthStatusResponse {

    @Schema(title = "健康检查是否启用，dev profile 下为 false")
    private boolean enabled;

    @Schema(title = "快照产出时间，为空表示尚未完成首轮探测")
    private LocalDateTime updatedAt;

    @Schema(title = "参与检查的服务器，当前线上只有一台")
    private List<ServerHealth> servers;

    public static FrontServerHealthStatusResponse from(FrontServerHealthSnapshot snapshot) {
        return FrontServerHealthStatusResponse.builder()
                .enabled(snapshot.enabled())
                .updatedAt(snapshot.updatedAt())
                .servers(snapshot.servers().stream().map(ServerHealth::from).toList())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "单台前端服务器健康状态")
    public static class ServerHealth {

        @Schema(title = "服务器名称")
        private String serverName;

        @Schema(title = "DNS 当前实际解析到的 IP，回查失败或尚未回查时为空")
        private String dnsIp;

        @Schema(title = "节点状态，恒定三条且顺序固定：主IP、备用IP、兜底IP")
        private List<NodeHealth> nodes;

        static ServerHealth from(FrontServerHealthSnapshot.ServerHealth server) {
            return ServerHealth.builder()
                    .serverName(server.serverName())
                    .dnsIp(server.dnsIp())
                    .nodes(server.nodes().stream().map(NodeHealth::from).toList())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(title = "单个 IP 节点健康状态")
    public static class NodeHealth {

        @Schema(title = "IP 角色")
        private FrontServerIpRole role;

        @Schema(title = "角色中文名，如「主IP」")
        private String roleLabel;

        @Schema(title = "IP 地址，未配置该角色时为空")
        private String ip;

        @Schema(title = "健康状态；未配置的角色恒为 UNKNOWN")
        private FrontServerHealthStatus status;

        @Schema(title = "该角色是否配置了 IP，用于区分「未配置」与「探测中」")
        private boolean configured;

        @Schema(title = "DNS 当前是否正指向该节点")
        private boolean active;

        static NodeHealth from(FrontServerHealthSnapshot.NodeHealth node) {
            return NodeHealth.builder()
                    .role(node.role())
                    .roleLabel(node.role().getLabel())
                    .ip(node.ip())
                    .status(node.status())
                    .configured(node.configured())
                    .active(node.active())
                    .build();
        }
    }
}
