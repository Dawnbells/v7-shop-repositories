package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.AiRateLimitMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class EditAiAccountRequest extends IdRequest {

    @NotBlank(message = "AI账号名称不能为空")
    @Schema(title = "AI账号名称")
    private String name;

    @Schema(title = "描述")
    private String description;

    @NotNull(message = "AI账号类型不能为空")
    @Schema(title = "AI账号类型", example = "GEMINI_OFFICIAL_STANDARD")
    private AiProvider provider;

    @NotBlank(message = "API Key不能为空")
    @Schema(title = "API Key")
    private String apiKey;

    @Schema(title = "Base URL")
    private String baseUrl;

    @Schema(title = "User-Agent")
    private String userAgent;

    @NotBlank(message = "模型不能为空")
    @Schema(title = "模型")
    private String model;

    @Schema(title = "文本输入价格")
    private BigDecimal textInputPrice;

    @Schema(title = "文本输入计费单位", example = "PER_1M_TOKENS")
    private AiBillingPriceUnit textInputPriceUnit;

    @Schema(title = "文本输出价格")
    private BigDecimal textOutputPrice;

    @Schema(title = "文本输出计费单位", example = "PER_1M_TOKENS")
    private AiBillingPriceUnit textOutputPriceUnit;

    @Schema(title = "图片输入价格")
    private BigDecimal imageInputPrice;

    @Schema(title = "图片输入计费单位", example = "PER_IMAGE")
    private AiBillingPriceUnit imageInputPriceUnit;

    @Schema(title = "图片输出价格")
    private BigDecimal imageOutputPrice;

    @Schema(title = "图片输出计费单位", example = "PER_IMAGE")
    private AiBillingPriceUnit imageOutputPriceUnit;

    @Schema(title = "视频输入价格")
    private BigDecimal videoInputPrice;

    @Schema(title = "视频输入计费单位", example = "PER_MINUTE")
    private AiBillingPriceUnit videoInputPriceUnit;

    @Schema(title = "视频输出价格")
    private BigDecimal videoOutputPrice;

    @Schema(title = "视频输出计费单位", example = "PER_MINUTE")
    private AiBillingPriceUnit videoOutputPriceUnit;

    @Schema(title = "计费币种")
    private String billingCurrency;

    @Schema(title = "每日限额")
    private Integer dailyLimit;

    @Schema(title = "AI账号流控模式", example = "CONCURRENCY")
    private AiRateLimitMode rateLimitMode;

    @Schema(title = "每日请求限制")
    private Integer requestsPerDay;

    @Schema(title = "每分钟请求限制")
    private Integer requestsPerMinute;

    @Schema(title = "最大并发数")
    private Integer maxConcurrency;

    @Schema(title = "优先级")
    private Integer priority;
}
