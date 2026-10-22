package cn.v7soft.admin.controller;

import cn.v7soft.admin.controller.req.ShopifyLandingResolveRequest;
import cn.v7soft.admin.service.IShopifyInstallService;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shopify 公开应用的公开端点（免登录，CompanyTenantInterceptor 已放行 /shopify-app/）：
 * App URL 落地、OAuth 回调、落地页解析后台地址。不返回任何租户信息，错误只返回错误码。
 */
@RestController
@IgnoreResponsePackage
@RequiredArgsConstructor
@RequestMapping("/shopify-app")
@Tag(name = "Shopify 公开应用")
public class ShopifyAppController {

    private final IShopifyInstallService installService;

    @GetMapping("/landing")
    @Operation(summary = "Shopify 打开 App URL", description = "已连接 → 结果页；否则签发票据并跳转授权")
    public ResponseEntity<Void> landing(HttpServletRequest request) {
        return redirect(installService.handleLanding(request.getParameterMap()));
    }

    @GetMapping("/oauth/callback")
    @Operation(summary = "Shopify OAuth 回调")
    public ResponseEntity<Void> callback(HttpServletRequest request) {
        return redirect(installService.handleCallback(request.getParameterMap()));
    }

    @PostMapping("/landing/resolve")
    @Operation(summary = "落地页解析后台地址", description = "成功返回后台绑定页地址；400 {code: TICKET_EXPIRED|UNKNOWN_HOST}")
    public ResponseEntity<Map<String, String>> resolve(@RequestBody(required = false) ShopifyLandingResolveRequest request) {
        try {
            String redirectUrl = installService.resolveLanding(request == null ? null : request.getTicket(),
                    request == null ? null : request.getAdminHost());
            return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
        } catch (IShopifyInstallService.InstallException e) {
            return ResponseEntity.badRequest().body(Map.of("code", e.getCode().name()));
        }
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
