package cn.v7soft.admin.controller.resp;

import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "订单统计个人配置")
public class OrderStatisticsConfigResponse {
    private String defaultTargetCurrencyCode;

    private String timeZoneId;

    @Builder.Default
    private Map<String, String> exchangeRates = new LinkedHashMap<>();

    public static OrderStatisticsConfigResponse convert(OrderStatisticsUserConfig config) {
        return OrderStatisticsConfigResponse.builder()
                .defaultTargetCurrencyCode(config.getDefaultTargetCurrencyCode())
                .timeZoneId(config.getTimeZoneId())
                .exchangeRates(new LinkedHashMap<>(config.getExchangeRates()))
                .build();
    }
}
