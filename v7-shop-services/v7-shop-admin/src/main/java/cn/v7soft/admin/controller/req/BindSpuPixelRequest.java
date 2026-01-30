package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 绑定/解绑SPU像素请求
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "绑定/解绑SPU像素请求")
public class BindSpuPixelRequest {

    @NotNull(message = "子域名ID不能为空")
    @Schema(title = "子域名ID", required = true)
    private Long subDomainId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(title = "SPU ID", required = true)
    private Long spuId;

    @NotNull(message = "像素ID不能为空")
    @Schema(title = "像素账号ID", required = true)
    private Long pixelId;
}

