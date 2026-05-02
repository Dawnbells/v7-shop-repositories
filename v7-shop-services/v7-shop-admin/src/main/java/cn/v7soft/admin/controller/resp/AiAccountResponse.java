package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.AiRateLimitMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@SuperBuilder
@Schema(description = "AI账号信息")
public class AiAccountResponse extends DataRangeResponse {

    private String name;
    private String description;
    private AiProvider provider;
    private String apiKey;
    private String baseUrl;
    private String userAgent;
    private String model;
    private BigDecimal textInputPrice;
    private AiBillingPriceUnit textInputPriceUnit;
    private BigDecimal textOutputPrice;
    private AiBillingPriceUnit textOutputPriceUnit;
    private BigDecimal imageInputPrice;
    private AiBillingPriceUnit imageInputPriceUnit;
    private BigDecimal imageOutputPrice;
    private AiBillingPriceUnit imageOutputPriceUnit;
    private BigDecimal videoInputPrice;
    private AiBillingPriceUnit videoInputPriceUnit;
    private BigDecimal videoOutputPrice;
    private AiBillingPriceUnit videoOutputPriceUnit;
    private String billingCurrency;
    private Integer dailyLimit;
    private AiRateLimitMode rateLimitMode;
    private Integer requestsPerDay;
    private Integer requestsPerMinute;
    private Integer maxConcurrency;
    private Integer priority;

    public static AiAccountResponse convertEntity(AiAccount entity) {
        return filling(entity, AiAccountResponse.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .provider(entity.getProvider())
                .apiKey(entity.getApiKey())
                .baseUrl(entity.getBaseUrl())
                .userAgent(entity.getUserAgent())
                .model(entity.getModel())
                .textInputPrice(entity.getTextInputPrice())
                .textInputPriceUnit(entity.getTextInputPriceUnit())
                .textOutputPrice(entity.getTextOutputPrice())
                .textOutputPriceUnit(entity.getTextOutputPriceUnit())
                .imageInputPrice(entity.getImageInputPrice())
                .imageInputPriceUnit(entity.getImageInputPriceUnit())
                .imageOutputPrice(entity.getImageOutputPrice())
                .imageOutputPriceUnit(entity.getImageOutputPriceUnit())
                .videoInputPrice(entity.getVideoInputPrice())
                .videoInputPriceUnit(entity.getVideoInputPriceUnit())
                .videoOutputPrice(entity.getVideoOutputPrice())
                .videoOutputPriceUnit(entity.getVideoOutputPriceUnit())
                .billingCurrency(entity.getBillingCurrency())
                .dailyLimit(entity.getDailyLimit())
                .rateLimitMode(entity.getRateLimitMode() == null ? AiRateLimitMode.CONCURRENCY : entity.getRateLimitMode())
                .requestsPerDay(entity.getRequestsPerDay())
                .requestsPerMinute(entity.getRequestsPerMinute())
                .maxConcurrency(entity.getMaxConcurrency() == null ? 1 : entity.getMaxConcurrency())
                .priority(entity.getPriority())
                .build());
    }
}
