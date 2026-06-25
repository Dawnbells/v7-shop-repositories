package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsMetricsResponse {
    private long orderCount;
    private long validOrderCount;
    private long invalidOrderCount;
    private long deliveredOrderCount;
    private long undeliveredOrderCount;
    private String deliveryRate;
    private String totalSalesAmount;
    private String invalidSalesAmount;
    private String undeliveredSalesAmount;
    private String deliveredSalesAmount;
    private long missingRateOrderCount;
}
