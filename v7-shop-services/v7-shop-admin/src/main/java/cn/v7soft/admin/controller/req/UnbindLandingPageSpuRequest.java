package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.LandingPageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 解绑落地页SPU请求（使用默认配置）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "解绑落地页SPU请求")
public class UnbindLandingPageSpuRequest {

    @NotNull(message = "子域名ID不能为空")
    @Schema(title = "子域名ID", required = true)
    private Long subDomainId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(title = "SPU ID", required = true)
    private Long spuId;

    @NotNull(message = "落地页类型不能为空")
    @Schema(title = "落地页类型", required = true, description = "real: 真实落地页, risk: 风险用户落地页, black: 黑名单落地页")
    private LandingPageType landingPageType;
}
