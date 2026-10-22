package cn.v7soft.admin.controller.req;

import cn.v7soft.dao.enums.CurrencyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台绑定页：用落地页带来的 bindTicket 绑定从 Shopify 发起安装的店铺
 */
@Getter
@Setter
public class ShopifyBindRequest {

    @NotBlank(message = "票据不存在或已过期")
    @Schema(title = "绑定票据", description = "落地页跳转带来的 bindTicket", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticket;

    @Schema(title = "店铺名称", description = "可空，新建时默认 handle，恢复时保留原名称")
    private String nickName;

    @Schema(title = "币种模式", description = "可空，新建时默认 SHOP_MONEY，恢复时保留原值", example = "SHOP_MONEY")
    private CurrencyMode currencyMode;
}
