package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存广告配置请求")
public class SaveAdConfigRequest {

    @NotNull(message = "子域名ID不能为空")
    @Schema(title = "子域名ID")
    private Long subDomainId;

    @NotNull(message = "SPU ID不能为空")
    @Schema(title = "SPU ID")
    private Long spuId;

    @Schema(title = "广告平台: META / GOOGLE / TIKTOK")
    private String adPlatform;

    @Schema(title = "流量媒介: cpc / ppc / organic / email / social / paid_social / affiliate / referral / display / video")
    private String medium;

    @Schema(title = "斗篷策略: NONE / DEFAULT / GOOGLE_NORMAL / GOOGLE_LENIENT / GOOGLE_STRICT / PHANTOM_ISOLATION")
    private String cloakStrategy;

    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Campaign仅允许英文字母、数字和下划线")
    @Schema(title = "推广活动标识")
    private String campaign;
}
