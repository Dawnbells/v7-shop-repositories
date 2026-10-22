package cn.v7soft.admin.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.impl.ShopifyWebhookService;
import cn.v7soft.admin.utils.ShopifyHmac;
import cn.v7soft.core.annotation.IgnoreResponsePackage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Shopify 公开应用 webhook 接收端点（公开，无登录；租户拦截器已对 /shopify-app/ 放行）。
 * <p>
 * 只做 HMAC 校验 + 去重落库，立即返回；业务处理由 ShopifyWebhookProcessor 异步完成。
 * 设计文档：docs/features/shopify-public-app/ARCH.md §5.3
 */
@Slf4j
@RestController
@IgnoreResponsePackage
@RequiredArgsConstructor
@RequestMapping("/shopify-app")
@Tag(name = "Shopify 公开应用 webhook")
public class ShopifyWebhookController {
    static final String HEADER_TOPIC = "X-Shopify-Topic";
    static final String HEADER_HMAC = "X-Shopify-Hmac-Sha256";
    static final String HEADER_SHOP_DOMAIN = "X-Shopify-Shop-Domain";
    static final String HEADER_WEBHOOK_ID = "X-Shopify-Webhook-Id";
    static final String HEADER_API_VERSION = "X-Shopify-API-Version";
    private static final int WEBHOOK_ID_MAX_LENGTH = 64;

    private final ShopifyAppProperties appProperties;
    private final ShopifyWebhookService webhookService;

    /**
     * 未配置应用 → 503；HMAC 不符 → 401 空体（不落库）；其余（含重复、不支持的 topic）→ 200；落库失败 → 500 让 Shopify 重投
     */
    @PostMapping("/webhooks")
    @Operation(summary = "接收 Shopify webhook")
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
                                        @RequestHeader(value = HEADER_TOPIC, required = false) String topic,
                                        @RequestHeader(value = HEADER_HMAC, required = false) String hmac,
                                        @RequestHeader(value = HEADER_SHOP_DOMAIN, required = false) String shopDomain,
                                        @RequestHeader(value = HEADER_WEBHOOK_ID, required = false) String webhookId,
                                        @RequestHeader(value = HEADER_API_VERSION, required = false) String apiVersion) {
        if (!appProperties.isConfigured()) {
            log.warn("[ShopifyWebhook] Shopify 公开应用未配置，拒绝 webhook: topic={}, shop={}", topic, shopDomain);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        byte[] raw = body == null ? new byte[0] : body;
        if (!ShopifyHmac.verifyBody(raw, hmac, appProperties.getClientSecret())) {
            log.warn("[ShopifyWebhook] HMAC 校验失败: topic={}, shop={}, webhookId={}", topic, shopDomain, webhookId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String payload = new String(raw, StandardCharsets.UTF_8);
        try {
            webhookService.enqueue(resolveWebhookId(webhookId, topic, payload), topic, shopDomain, apiVersion, payload);
        } catch (Exception e) {
            log.error("[ShopifyWebhook] 事件落库失败: topic={}, shop={}, webhookId={}", topic, shopDomain, webhookId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 正常情况下 Shopify 总会带 X-Shopify-Webhook-Id；缺失时用 topic + 正文的 SHA-256 作为去重键
     */
    static String resolveWebhookId(String webhookId, String topic, String payload) {
        if (StrUtil.isNotBlank(webhookId)) {
            String trimmed = webhookId.trim();
            return trimmed.length() > WEBHOOK_ID_MAX_LENGTH ? SecureUtil.sha256(trimmed) : trimmed;
        }
        return SecureUtil.sha256(StrUtil.nullToEmpty(topic) + "\n" + payload);
    }
}
