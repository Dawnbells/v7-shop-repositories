package cn.v7soft.common.controller.resp;


import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.FrontServer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "前端服务器响应")
public class FrontServerResponse extends IdResponse {

    @Schema(title = "服务器名称", example = "Server-1")
    private String name;

    @Schema(title = "CNAME记录", description = "CNAME域名")
    private String cnameRecord;

    @Schema(title = "主IP地址", example = "192.168.1.1")
    private String primaryIp;

    @Schema(title = "故障转移IP地址", example = "192.168.1.2")
    private String failoverIp;

    @Schema(title = "健康检查地址", example = "https://domain.com/health")
    private String healthCheckUrl;

    @Schema(title = "是否已发生IP故障切换", example = "false")
    private boolean ipSwitched;

    @Schema(title = "解析次数", example = "100")
    private int resolutionCount;

    @Schema(title = "当前有效解析数量", example = "80")
    private int activeResolutionCount;

    @Schema(title = "是否需要更新", example = "false")
    private boolean requiredUpdate;

    /**
     * 从 `FrontServer` 实体转换为 `FrontServerResponse` 的静态方法
     *
     * @param frontServer 前端服务器实体
     * @return 前端服务器响应对象
     */
    public static FrontServerResponse convertEntity(FrontServer frontServer) {
        return filling(frontServer, FrontServerResponse.builder()
                .name(frontServer.getName())
                .cnameRecord(frontServer.getCnameRecord())
                .primaryIp(frontServer.getPrimaryIp())
                .failoverIp(frontServer.getFailoverIp())
                .healthCheckUrl(frontServer.getHealthCheckUrl())
                .ipSwitched(frontServer.isIpSwitched())
                .resolutionCount(frontServer.getResolutionCount())
                .activeResolutionCount(frontServer.getActiveResolutionCount())
                .requiredUpdate(frontServer.isRequiredUpdate())
                .build());
    }
}
