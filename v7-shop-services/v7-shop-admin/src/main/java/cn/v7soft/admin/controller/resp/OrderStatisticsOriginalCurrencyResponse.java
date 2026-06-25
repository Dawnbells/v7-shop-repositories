package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsOriginalCurrencyResponse {
    private String currencyCode;
    private long orderCount;
    private String totalAmount;
    private String invalidAmount;
    private String undeliveredAmount;
    private String deliveredAmount;
}
