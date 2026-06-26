package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsResultResponse {
    private String targetCurrencyCode;
    private OrderStatisticsMetricsResponse summary;
    private List<OrderStatisticsBucketResponse> buckets;
    private List<OrderStatisticsGroupResponse> groups;
    private List<OrderStatisticsBucketGroupResponse> bucketGroups;
    private List<OrderStatisticsOriginalCurrencyResponse> originalCurrencies;
    private List<OrderStatisticsMissingRateResponse> missingRates;
}
