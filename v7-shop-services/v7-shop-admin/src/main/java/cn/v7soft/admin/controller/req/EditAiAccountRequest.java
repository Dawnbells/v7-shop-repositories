package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiBillingPriceUnit;
import cn.v7soft.dao.enums.AiProvider;
import cn.v7soft.dao.enums.InvokeMode;
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
    @Schema(title = "AI账号名称", example = "Gemini官方账号")
    private String name;

    @Schema(title = "描述")
    private String description;

    @NotNull(message = "AI服务商不能为空")
    @Schema(title = "AI服务商", example = "GEMINI")
    private AiProvider provider;

    @NotNull(message = "API渠道不能为空")
    @Schema(title = "API渠道", example = "OFFICIAL")
    private AiApiChannel apiChannel;

    @Schema(title = "Gemini接口模式", description = "STANDARD为标准接口，BATCH为批量接口", example = "STANDARD")
    private InvokeMode invokeMode;

    @NotBlank(message = "API Key不能为空")
    @Schema(title = "API Key")
    private String apiKey;

    @Schema(title = "Base URL", description = "Sub2API或代理接口地址")
    private String baseUrl;

    @NotBlank(message = "模型不能为空")
    @Schema(title = "模型", description = "一个AI账号只对应一个模型；多个模型请创建多个AI账号")
    private String model;

    @Schema(title = "文本输入价格", description = "文本输入计费价格")
    private BigDecimal textInputPrice;

    @Schema(title = "文本输入计费单位", example = "PER_1M_TOKENS")
    private AiBillingPriceUnit textInputPriceUnit;

    @Schema(title = "文本输出价格", description = "文本输出计费价格")
    private BigDecimal textOutputPrice;

    @Schema(title = "文本输出计费单位", example = "PER_1M_TOKENS")
    private AiBillingPriceUnit textOutputPriceUnit;

    @Schema(title = "图片输入价格", description = "图片输入计费价格")
    private BigDecimal imageInputPrice;

    @Schema(title = "图片输入计费单位", example = "PER_IMAGE")
    private AiBillingPriceUnit imageInputPriceUnit;

    @Schema(title = "图片输出价格", description = "图片输出计费价格")
    private BigDecimal imageOutputPrice;

    @Schema(title = "图片输出计费单位", example = "PER_IMAGE")
    private AiBillingPriceUnit imageOutputPriceUnit;

    @Schema(title = "视频输入价格", description = "视频输入计费价格")
    private BigDecimal videoInputPrice;

    @Schema(title = "视频输入计费单位", example = "PER_MINUTE")
    private AiBillingPriceUnit videoInputPriceUnit;

    @Schema(title = "视频输出价格", description = "视频输出计费价格")
    private BigDecimal videoOutputPrice;

    @Schema(title = "视频输出计费单位", example = "PER_MINUTE")
    private AiBillingPriceUnit videoOutputPriceUnit;

    @Schema(title = "计费币种", description = "如USD、CNY")
    private String billingCurrency;

    @Schema(title = "每日限额", description = "为空表示不单独限制")
    private Integer dailyLimit;

    @Schema(title = "优先级", description = "数值越小越优先")
    private Integer priority;
}
