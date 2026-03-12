package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.LandingPageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 绑定落地页SPU请求
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "绑定落地页SPU请求")
public class BindLandingPageSpuRequest {

    @NotNull(message = "子域名ID不能为空")
    @Schema(title = "子域名ID")
    private Long subDomainId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(title = "SPU ID")
    private Long spuId;

    @NotNull(message = "落地页显示的SPU ID不能为空")
    @Schema(title = "落地页显示的SPU ID")
    private Long landingSpuId;

    @NotNull(message = "落地页类型不能为空")
    @Schema(title = "落地页类型", description = "LAND: 真实落地页, CLOAK: 风险用户落地页, BLACKLISTED: 黑名单落地页")
    private LandingPageType landingPageType;
}
