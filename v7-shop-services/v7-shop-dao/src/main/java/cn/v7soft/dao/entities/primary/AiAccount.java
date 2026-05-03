package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.converter.AiProviderConverter;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.AiRateLimitMode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "t_ai_accounts", indexes = {
        @Index(name = "idx_ai_account_provider", columnList = "provider"),
        @Index(name = "idx_ai_account_priority", columnList = "priority")
})
@SQLRestriction("status <> 'DELETED'")
public class AiAccount extends BaseDataRangeEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Convert(converter = AiProviderConverter.class)
    @Column(name = "provider", nullable = false, length = 40)
    private AiProvider provider;

    @Column(name = "api_key", nullable = false, length = 2048)
    private String apiKey;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "text_input_price", precision = 12, scale = 6)
    private BigDecimal textInputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_input_price_unit", length = 30)
    private AiBillingPriceUnit textInputPriceUnit;

    @Column(name = "text_output_price", precision = 12, scale = 6)
    private BigDecimal textOutputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "text_output_price_unit", length = 30)
    private AiBillingPriceUnit textOutputPriceUnit;

    @Column(name = "image_input_price", precision = 12, scale = 6)
    private BigDecimal imageInputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_input_price_unit", length = 30)
    private AiBillingPriceUnit imageInputPriceUnit;

    @Column(name = "image_output_price", precision = 12, scale = 6)
    private BigDecimal imageOutputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_output_price_unit", length = 30)
    private AiBillingPriceUnit imageOutputPriceUnit;

    @Column(name = "video_input_price", precision = 12, scale = 6)
    private BigDecimal videoInputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_input_price_unit", length = 30)
    private AiBillingPriceUnit videoInputPriceUnit;

    @Column(name = "video_output_price", precision = 12, scale = 6)
    private BigDecimal videoOutputPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_output_price_unit", length = 30)
    private AiBillingPriceUnit videoOutputPriceUnit;

    @Column(name = "billing_currency", length = 10)
    private String billingCurrency;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_mode", length = 20)
    private AiRateLimitMode rateLimitMode = AiRateLimitMode.CONCURRENCY;

    @Column(name = "requests_per_day")
    private Integer requestsPerDay;

    @Column(name = "requests_per_minute")
    private Integer requestsPerMinute;

    @Builder.Default
    @Column(name = "max_concurrency")
    private Integer maxConcurrency = 1;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;
}
