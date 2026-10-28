package cn.v7soft.admin.config;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shopify 公开应用配置（shopify.app.*）。
 * <p>
 * client-id / client-secret 为空时公开应用相关功能不可用（返回「未配置」），凭证模式不受影响。
 * 设计文档：docs/features/shopify-public-app/ARCH.md §2
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "shopify.app")
public class ShopifyAppProperties {

    /**
     * 公开应用的 Client ID
     */
    private String clientId;

    /**
     * 公开应用的 Client Secret（同时用于 OAuth 查询串与 webhook 的 HMAC 校验）
     */
    private String clientSecret;

    /**
     * 中立域名，含协议，无尾斜杠，例如 https://shopify.example.com
     */
    private String appHost;

    /**
     * Admin API 版本
     */
    private String apiVersion = "2026-07";

    /**
     * OAuth 申请的 scope，逗号分隔
     */
    private String scopes = "read_orders,read_products";

    /**
     * 安装成功后是否用 GraphQL 注册订单 webhook（TOML 已声明时保持 false）
     */
    private boolean registerWebhooksOnInstall = false;

    /**
     * 安装票据有效期（分钟）
     */
    private int installTicketTtlMinutes = 10;

    /**
     * 带待绑定 token 的票据有效期（分钟）
     */
    private int pendingTicketTtlMinutes = 30;

    /**
     * 后台「连接 Shopify」意向（及其 cookie）的有效期（分钟），需覆盖把链接转给店主安装的时间
     */
    private int connectIntentTtlMinutes = 60;

    /**
     * 应用在 Shopify App Store 的安装页，例如 https://apps.shopify.com/xxx；
     * 为空时（上架批准前）中转地址跳到说明页，提示商家从 Shopify 后台打开应用
     */
    private String installUrl;

    /**
     * 公开应用对账间隔（分钟）
     */
    private int reconcileIntervalMinutes = 15;

    /**
     * 对账回看时长（分钟）
     */
    private int reconcileLookbackMinutes = 30;

    /**
     * Shopify 订单个人信息保留天数，到期脱敏
     */
    private int piiRetentionDays = 365;

    /**
     * 受保护数据访问日志保留天数
     */
    private int accessLogRetentionDays = 365;

    /**
     * client-id 与 client-secret 均已配置
     */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(clientId) && StrUtil.isNotBlank(clientSecret);
    }
}
