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

    @Schema(title = "授权地址", description = "前端直接 window.location.href 跳转")
    private String authorizeUrl;
}
