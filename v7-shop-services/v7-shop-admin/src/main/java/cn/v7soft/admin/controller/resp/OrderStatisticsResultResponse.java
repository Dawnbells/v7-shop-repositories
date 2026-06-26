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
    /** 统计生成时间（=数据截止时刻），ISO-8601 UTC Instant 串，如 2026-06-25T07:30:00Z，随快照冻结。 */
    private String generatedAt;
    /** 用户配置的 IANA 时区 ID（如 Asia/Shanghai），生成时刻冻结，用于按用户时区渲染生成时间。 */
    private String timeZoneId;
}
