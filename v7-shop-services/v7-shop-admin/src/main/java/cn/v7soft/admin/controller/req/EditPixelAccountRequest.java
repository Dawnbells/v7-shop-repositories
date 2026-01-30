package cn.v7soft.admin.controller.req;

import java.util.List;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.PixelAccountPlatform;
import cn.v7soft.dao.enums.PixelTrackingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 用于编辑像素账号信息的请求类。
 */
@Getter
@Setter
public class EditPixelAccountRequest extends IdRequest {

    @NotBlank(message = "像素名称不能为空")
    @Schema(title = "像素名称", example = "My Pixel Account", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pixelName;

    @NotBlank(message = "像素ID不能为空")
    @Schema(title = "像素ID", example = "123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pixelId;

    @Schema(title = "AccessToken", example = "abcdefg123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @NotNull(message = "平台不能为空")
    @Schema(title = "平台", example = "FACEBOOK", requiredMode = Schema.RequiredMode.REQUIRED)
    private PixelAccountPlatform platform;

    @NotNull(message = "追踪类型不能为空")
    @Schema(title = "追踪类型", example = "STANDARD", requiredMode = Schema.RequiredMode.REQUIRED)
    private PixelTrackingType trackingType;

    @NotNull(message = "转化事件不能为空, FB、TK使用")
    @Schema(title = "转化事件", example = "AddToCart", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conversionEvent;

    @NotNull(message = "产品列表不能为空")
    @Schema(title = "产品列表", example ="[\"1259518722048\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> productIds;
}
