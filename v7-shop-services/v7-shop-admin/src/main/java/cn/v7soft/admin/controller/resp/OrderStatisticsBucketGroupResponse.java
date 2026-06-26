package cn.v7soft.admin.controller.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsBucketGroupResponse {
    private String bucketKey;
    private String groupKey;
    private String id;
    private String name;
    private boolean historical;
    private OrderStatisticsMetricsResponse metrics;
}