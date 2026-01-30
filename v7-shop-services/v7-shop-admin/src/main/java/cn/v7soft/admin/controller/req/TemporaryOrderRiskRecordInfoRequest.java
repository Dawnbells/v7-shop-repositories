package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.BrowserPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "风险记录信息请求实体类")
public class TemporaryOrderRiskRecordInfoRequest {

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "远程IP")
    private String remoteIp;

    @Schema(description = "远程IP信息")
    private String remoteIpInfo;

    @Schema(description = "真实IP")
    private String realIp;

    @Schema(description = "真实IP信息")
    private String realIpInfo;

    @Schema(description = "用户代理")
    private String ua;

    @Schema(description = "请求参数键")
    private String pdKey;

    @Schema(description = "请求参数值")
    private String pdVal;

    @Schema(description = "是否为隐匿浏览器")
    private Boolean cloak;

    @Schema(description = "浏览器平台")
    private BrowserPlatform browserPlatform;
}
