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
 * Shopify 合规 webhook（shop/redact、customers/redact、customers/data_request）的处理记录（全局表，不分租户）。
 * 合规要求 30 天内处理完毕，记录用于追溯。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_shopify_compliance_records", indexes = {
        @Index(name = "idx_shopify_compliance_shop_domain", columnList = "shop_domain"),
        @Index(name = "idx_shopify_compliance_state", columnList = "state")
})
public class ShopifyComplianceRecord extends BaseEntity {

    /**
     * 处理状态
     */
    public enum State {
        /** 待处理 */
        PENDING,
        /** 已处理 */
        DONE,
        /** 交人工处理（customers/data_request） */
        MANUAL
    }

    /**
     * shop/redact、customers/redact、customers/data_request
     */
    @Column(name = "topic", length = 64)
    private String topic;

    @Column(name = "shop_domain", length = 128)
    private String shopDomain;

    @Column(name = "website_id")
    private Long websiteId;

    /**
     * 店铺所属公司（不走租户过滤）
     */
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_phone", length = 64)
    private String customerPhone;

    /**
     * Shopify 订单 ID，逗号分隔
     */
    @Column(name = "order_ids", columnDefinition = "TEXT")
    private String orderIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16)
    @Builder.Default
    private State state = State.PENDING;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /**
     * 处理截止时间（received_at + 30 天）
     */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 脱敏影响行数
     */
    @Column(name = "affected_count")
    private Integer affectedCount;

    @Column(name = "note", length = 1000)
    private String note;
}
