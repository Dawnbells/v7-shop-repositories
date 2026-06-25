package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsBucketResponse {
    private String key;
    private String startAt;
    private String endAt;
    private boolean partial;
    private OrderStatisticsMetricsResponse metrics;
}
