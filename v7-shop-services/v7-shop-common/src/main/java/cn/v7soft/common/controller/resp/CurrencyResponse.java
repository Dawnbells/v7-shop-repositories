package cn.v7soft.common.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 用于返回货币信息的响应类。
 */
@Getter
@Setter
@SuperBuilder
@Schema(description = "货币信息响应")
public class CurrencyResponse extends IdResponse {
    /**
     * 货币名称
     */
    @Schema(title = "货币名称", example = "美元")
    private String name;

    /**
     * 货币符号
     */
    @Schema(title = "货币符号", example = "$")
    private String symbol;

    /**
     * 货币代码
     */
    @Schema(title = "货币代码", example = "USD")
    private String code;

    /**
     * 美元兑换汇率
     */
    @Schema(title = "美元兑换汇率", example = "6.5")
    private BigDecimal exchangeRate;

    /**
     * 有效小数位
     */
    @Schema(title = "有效小数位", example = "2")
    private int fractionDigits;

    public static CurrencyResponse convertEntity(Currency currency) {
        if (currency == null) {
            return null;
        }
        return filling(currency, CurrencyResponse.builder()
                .id(String.valueOf(currency.getId()))
                .name(currency.getName())
                .code(currency.getCode())
                .symbol(currency.getSymbol())
                .exchangeRate(currency.getExchangeRate())
                .fractionDigits(currency.getFractionDigits())
                .build());
    }
}
