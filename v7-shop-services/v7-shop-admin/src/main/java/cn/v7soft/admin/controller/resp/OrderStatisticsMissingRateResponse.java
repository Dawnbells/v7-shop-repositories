package cn.v7soft.admin.controller.resp;

import cn.v7soft.admin.statistics.OrderStatisticsMissingRateReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsMissingRateResponse {
    private String currencyCode;
    private OrderStatisticsMissingRateReason reason;
    private long orderCount;
    private String originalAmount;
}
