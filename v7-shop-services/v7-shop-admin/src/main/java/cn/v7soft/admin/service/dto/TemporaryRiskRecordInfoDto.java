package cn.v7soft.admin.service.dto;

import cn.hutool.core.bean.BeanUtil;
import cn.v7soft.dao.dto.IdDto;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.entities.primary.TemporaryOrderRiskRecordInfo;
import cn.v7soft.dao.enums.BrowserPlatform;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TemporaryRiskRecordInfoDto extends IdDto {
    /**
     * 设备ID
     */
    private String deviceId;
    /**
     * 远程IP地址
     */
    private String remoteIp;
    /**
     * 远程IP地址信息
     */
    private String remoteIpInfo;

    /**
     * 真实IP地址,用逗号分隔
     */
    private String realIp;

    /**
     * 真实IP地址信息
     */
    private String realIpInfo;

    /**
     * UA信息
     */
    private String ua;

    /**
     * key
     */
    private String pdKey;
    /**
     * pd value
     */
    private String pdVal;

    /**
     * 下单平台
     */
    private BrowserPlatform browserPlatform;
    /**
     * 是否斗篷单
     */
    private Boolean cloak;
    public static TemporaryRiskRecordInfoDto convert(TemporaryOrderRiskRecordInfo riskInfo) {
        TemporaryRiskRecordInfoDto riskRecordInfoDto = new TemporaryRiskRecordInfoDto();
        BeanUtil.copyProperties(riskInfo, riskRecordInfoDto);
        return riskRecordInfoDto;
    }

    public OrderRiskRecordInfo toOrderRiskInfo() {
        OrderRiskRecordInfo riskRecordInfoDto = new OrderRiskRecordInfo();
        BeanUtil.copyProperties(this, riskRecordInfoDto);
        riskRecordInfoDto.setId(null);
        return riskRecordInfoDto;
    }
}
