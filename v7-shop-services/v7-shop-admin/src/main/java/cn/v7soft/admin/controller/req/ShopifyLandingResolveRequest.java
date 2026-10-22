package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 落地页提交商家的后台地址
 */
@Getter
@Setter
public class ShopifyLandingResolveRequest {

    @Schema(title = "STORE 票据", description = "落地页 query 中的 ticket")
    private String ticket;

    @Schema(title = "后台地址", description = "xxx.example.com 或 https://xxx.example.com/...")
    private String adminHost;
}
