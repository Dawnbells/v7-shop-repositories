package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.CurrencyMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台「连接 Shopify」：新增返回中转连接地址（不含店铺标识，店铺由商家在 Shopify 中选择）；带 id 表示重新连接已有记录
 */
@Getter
@Setter
public class ShopifyInstallUrlRequest extends IdRequest {

    @Schema(title = "店铺名称", description = "可空，新建时默认 Shopify 店铺名，重连时默认原名称")
    private String nickName;

    @Schema(title = "币种模式", description = "SHOP_MONEY=店铺结算币种, PRESENTMENT_MONEY=订单展示币种", example = "SHOP_MONEY")
    private CurrencyMode currencyMode;
}
