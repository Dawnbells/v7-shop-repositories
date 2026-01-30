package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 用于编辑货币信息的请求类。
 */
@Getter
@Setter
public class EditCurrencyRequest extends IdRequest {
    /**
     * 货币名称
     */
    @NotBlank(message = "货币名称不能为空")
    @Size(max = 50, message = "货币名称不能超过50个字符")
    @Schema(title = "货币名称", example = "美元", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 货币符号
     */
    @Size(max = 10, message = "货币符号不能超过10个字符")
    @Schema(title = "货币符号", example = "$")
    private String symbol;

    /**
     * 货币代码
     */
    @NotBlank(message = "货币代码不能为空")
    @Size(max = 10, message = "货币代码不能超过10个字符")
    @Schema(title = "货币代码", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    /**
     * 美元兑换汇率
     */
    @Schema(title = "美元兑换汇率", example = "6.5")
    private BigDecimal exchangeRate;

    /**
     * 有效小数位
     */
    @Positive(message = "有效小数位必须大于0")
    @Schema(title = "有效小数位", example = "2")
    private int fractionDigits;
}
