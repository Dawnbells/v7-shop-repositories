package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelTrackingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "新增并绑定SPU像素请求")
public class CreateAndBindSpuPixelRequest {

    @NotNull(message = "子域名ID不能为空")
    @Schema(title = "子域名ID")
    private Long subDomainId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(title = "SPU ID")
    private Long spuId;

    @NotBlank(message = "像素名称不能为空")
    @Schema(title = "像素名称")
    private String pixelName;

    @NotBlank(message = "像素ID不能为空")
    @Schema(title = "像素ID")
    private String pixelId;

    @Schema(title = "AccessToken / Org ID")
    private String accessToken;

    @NotNull(message = "平台不能为空")
    @Schema(title = "平台")
    private PixelAccountPlatform platform;

    @NotNull(message = "追踪类型不能为空")
    @Schema(title = "追踪类型")
    private PixelTrackingType trackingType;

    @NotBlank(message = "转化事件不能为空")
    @Schema(title = "转化事件")
    private String conversionEvent;

    @Schema(title = "嵌入像素HTML代码")
    private String embedCode;
}
