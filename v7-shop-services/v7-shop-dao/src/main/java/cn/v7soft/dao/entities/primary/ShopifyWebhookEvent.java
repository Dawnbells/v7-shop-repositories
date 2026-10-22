package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Shopify webhook 事件（全局表，不分租户）。
 * <p>
 * webhook 端点校验 HMAC 后先落库再立即返回 200，由定时处理器异步消费；
 * {@code webhook_id}（X-Shopify-Webhook-Id）唯一，用于去重。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_shopify_webhook_events", indexes = {
        @Index(name = "uk_webhook_id", columnList = "webhook_id", unique = true),
        @Index(name = "idx_shopify_webhook_shop_domain", columnList = "shop_domain"),
        @Index(name = "idx_shopify_webhook_state", columnList = "state")
})
public class ShopifyWebhookEvent extends BaseEntity {

    /**
     * 事件处理状态
     */
    public enum State {
        /** 待处理 */
        PENDING,
        /** 已处理 */
        DONE,
        /** 重试多次仍失败 */
        FAILED,
        /** 不支持的 topic 或店铺不存在，不处理 */
        IGNORED
    }

    /**
     * X-Shopify-Webhook-Id
     */
    @Column(name = "webhook_id", nullable = false, length = 64)
    private String webhookId;

    /**
     * X-Shopify-Topic，例如 orders/create
     */
    @Column(name = "topic", length = 64)
    private String topic;

    /**
     * X-Shopify-Shop-Domain，例如 xxx.myshopify.com
     */
    @Column(name = "shop_domain", length = 128)
    private String shopDomain;

    /**
     * X-Shopify-API-Version
     */
    @Column(name = "api_version", length = 16)
    private String apiVersion;

    /**
     * 按 shop_domain 解析到的店铺，找不到为 null
     */
    @Column(name = "website_id")
    private Long websiteId;

    /**
     * 店铺所属公司（不走租户过滤）
     */
    @Column(name = "company_id")
    private Long companyId;

    /**
     * 原始正文
     */
    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16)
    @Builder.Default
    private State state = State.PENDING;

    @Column(name = "attempts")
    @Builder.Default
    private Integer attempts = 0;

    /**
     * 下次可处理时间（失败退避）
     */
    @Column(name = "next_attempt_at")
    @Builder.Default
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    /**
     * 最近一次错误
     */
    @Column(name = "error", length = 1000)
    private String error;

    @Column(name = "received_at")
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
