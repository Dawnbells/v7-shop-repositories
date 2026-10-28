package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Shopify 公开应用授权地址")
public class ShopifyInstallUrlResponse {

    @Schema(title = "授权地址", description = "新增为中转连接地址（可复制到已登录 Shopify 的浏览器打开），重连为 Shopify 授权地址")
    private String authorizeUrl;
}
