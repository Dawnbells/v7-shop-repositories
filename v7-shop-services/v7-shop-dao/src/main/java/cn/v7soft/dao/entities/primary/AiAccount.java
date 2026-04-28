package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
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
        @Index(name = "idx_ai_account_channel", columnList = "api_channel"),
        @Index(name = "idx_ai_account_enabled", columnList = "enabled"),
        @Index(name = "idx_ai_account_priority", columnList = "priority")
})
@SQLRestriction("status <> 'DELETED'")
public class AiAccount extends BaseDataRangeEntity {

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AiProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "api_channel", nullable = false, length = 20)
    private AiApiChannel apiChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoke_mode", length = 20)
    private InvokeMode invokeMode;

    @Column(name = "api_key", nullable = false, length = 2048)
    private String apiKey;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

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
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
