package cn.v7soft.admin.controller.resp;

import cn.v7soft.common.controller.resp.DataRangeResponse;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
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
    private AiApiChannel apiChannel;
    private String apiKey;
    private String baseUrl;
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
    private Integer priority;
    private Boolean enabled;

    public static AiAccountResponse convertEntity(AiAccount entity) {
        return filling(entity, AiAccountResponse.builder()
                .name(entity.getName())
                .description(entity.getDescription())
                .provider(entity.getProvider())
                .apiChannel(entity.getApiChannel())
                .apiKey(entity.getApiKey())
                .baseUrl(entity.getBaseUrl())
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
                .priority(entity.getPriority())
                .enabled(entity.getEnabled())
                .build());
    }
}
