package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.IpBlacklist;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "IP黑名单响应")
public class IpBlacklistResponse extends DataRangeResponse {

    @Schema(title = "IP地址")
    private String ipAddress;

    @Schema(title = "浏览器指纹")
    private String fingerprint;

    @Schema(title = "备注")
    private String remark;

    public static IpBlacklistResponse convertEntity(IpBlacklist entity) {
        if (entity == null) {
            return null;
        }
        return IpBlacklistResponse.builder()
                .ipAddress(entity.getIpAddress())
                .fingerprint(entity.getFingerprint())
                .remark(entity.getRemark())
                .build();
    }
}
