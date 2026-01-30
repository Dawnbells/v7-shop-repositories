package cn.v7soft.admin.controller.resp;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.enums.BrowserPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@SuperBuilder
@Schema(description = "风险记录响应")
public class RiskRecordInfoResponse {

    /**
     * 远程IP地址
     */
    @Schema(title = "远程IP地址")
    private RiskIpResponse remoteIp;

    /**
     * 真实IP地址,用逗号分隔
     */
    @Schema(title = "真实IP地址")
    private RiskIpResponse realIp;

    @Schema(title = "设备IP")
    private String deviceId;
    /**
     * 下单平台
     */
    @Schema(title = "下单平台")
    private BrowserPlatform browserPlatform;

    public static RiskRecordInfoResponse convert(OrderRiskRecordInfo riskRecordInfo) {
        String remoteIpInfo = riskRecordInfo.getRemoteIpInfo();
        String realIpInfo = riskRecordInfo.getRealIpInfo();
        RiskIpResponse remoteIpResponse = StrUtil.isBlank(remoteIpInfo)
                                          ? RiskIpResponse.builder().ip(riskRecordInfo.getRemoteIp()).build()
                                          : JSONUtil.toBean(remoteIpInfo, RiskIpResponse.class);
        RiskIpResponse riskIpResponse = StrUtil.isBlank(realIpInfo)
                                        ? RiskIpResponse.builder().ip(riskRecordInfo.getRealIp()).build()
                                        : JSONUtil.toBean(realIpInfo, RiskIpResponse.class);

        return RiskRecordInfoResponse.builder()
                .remoteIp(remoteIpResponse)
                .realIp(riskIpResponse)
                .deviceId(riskRecordInfo.getDeviceId())
                .browserPlatform(riskRecordInfo.getBrowserPlatform())
                .build();
    }
}
