package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditIpBlacklistRequest extends IdRequest {

    @Schema(title = "IP地址", example = "192.168.1.1")
    private String ipAddress;

    @Schema(title = "浏览器指纹", example = "12023992392392939233333333923923")
    private String fingerprint;

    @Schema(title = "备注", example = "恶意爬虫")
    private String remark;
}
