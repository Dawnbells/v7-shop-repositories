package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditThirdPartyWebsiteRequest extends IdRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Schema(title = "店铺名称", example = "example-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickName;

    @NotBlank(message = "店铺的唯一标识不能为空")
    @Schema(title = "店铺的唯一标识", example = "example-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String handle;

    @NotBlank(message = "Token不能为空")
    @Schema(title = "令牌", example = "example-token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;

    @NotNull(message = "店铺类型不能为空")
    @Schema(title = "店铺类型", example = "SHOPLINE", requiredMode = Schema.RequiredMode.REQUIRED)
    private WebsiteTypeEnum websiteType;

    @Schema(title = "币种模式", description = "SHOP_MONEY=店铺结算币种, PRESENTMENT_MONEY=订单展示币种", example = "SHOP_MONEY")
    private CurrencyMode currencyMode;
}
