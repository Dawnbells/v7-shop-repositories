package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.CurrencyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditShopifyWebsiteRequest extends IdRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Schema(title = "店铺名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickName;

    @NotBlank(message = "店铺的唯一标识不能为空")
    @Schema(title = "店铺的唯一标识", description = "xxx.myshopify.com 中的 xxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String handle;

    @NotBlank(message = "Client ID不能为空")
    @Schema(title = "Shopify 应用的 Client ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientId;

    @Schema(title = "Shopify 应用的 Client Secret", description = "新增时必填，编辑时留空表示不修改")
    private String clientSecret;

    @Schema(title = "币种模式", description = "SHOP_MONEY=店铺结算币种, PRESENTMENT_MONEY=订单展示币种", example = "SHOP_MONEY")
    private CurrencyMode currencyMode;
}
