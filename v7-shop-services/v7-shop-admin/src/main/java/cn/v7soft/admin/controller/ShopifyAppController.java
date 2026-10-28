package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.ShopifyLandingResolveRequest;
import cn.v7soft.admin.service.IShopifyInstallService;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Shopify 公开应用的公开端点（免登录，CompanyTenantInterceptor 已放行 /shopify-app/）：
 * 后台连接中转、App URL 落地、OAuth 回调、落地页解析后台地址。不返回任何租户信息，错误只返回错误码。
 */
@RestController
@IgnoreResponsePackage
@RequiredArgsConstructor
@RequestMapping("/shopify-app")
@Tag(name = "Shopify 公开应用")
public class ShopifyAppController {
    /**
     * 连接意向 cookie：只存意向 nonce。Lax 保证 Shopify 安装后顶层跳回 /landing 时会带上
     */
    static final String INTENT_COOKIE = "v7_shopify_connect";
    private static final String INTENT_COOKIE_PATH = "/shopify-app/";

    private final IShopifyInstallService installService;
    private final ShopifyAppProperties appProperties;

    @GetMapping("/connect")
    @Operation(summary = "后台连接中转", description = "记住连接意向后跳 App Store 安装页；意向无效跳错误页")
    public ResponseEntity<Void> connect(@RequestParam(value = "intent", required = false) String intent) {
        return redirect(installService.handleConnect(intent));
    }

    @GetMapping("/landing")
    @Operation(summary = "Shopify 打开 App URL", description = "已连接 → 租户后台；否则签发票据并跳转授权（有连接意向时以意向的租户授权）")
    public ResponseEntity<Void> landing(HttpServletRequest request,
                                        @CookieValue(value = INTENT_COOKIE, required = false) String intent) {
        return redirect(installService.handleLanding(request.getParameterMap(), intent));
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

    private ResponseEntity<Void> redirect(IShopifyInstallService.Redirect redirect) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect.location())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        switch (redirect.intentCookie()) {
            case SET -> builder.header(HttpHeaders.SET_COOKIE,
                    intentCookie(redirect.intent(), Duration.ofMinutes(appProperties.getConnectIntentTtlMinutes())));
            case CLEAR -> builder.header(HttpHeaders.SET_COOKIE, intentCookie("", Duration.ZERO));
            case KEEP -> {
            }
        }
        return builder.build();
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private String intentCookie(String value, Duration maxAge) {
        return ResponseCookie.from(INTENT_COOKIE, value)
                .path(INTENT_COOKIE_PATH)
                .maxAge(maxAge)
                .httpOnly(true)
                // 本地 http 调试时不加 Secure，否则浏览器不保存
                .secure(StrUtil.startWithIgnoreCase(appProperties.getAppHost(), "https://"))
                .sameSite("Lax")
                .build()
                .toString();
    }
}
