package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(title = "订单统计币种候选")
public class OrderStatisticsCurrencyOptionResponse {
    private String code;
    private String name;
    private String symbol;
    private String exchangeRate;
    private int fractionDigits;
}
