package cn.v7soft.admin.controller.resp;

import cn.v7soft.admin.service.impl.OrderStatisticsQueryJob;
import cn.v7soft.admin.service.impl.OrderStatisticsQueryJobState;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class OrderStatisticsQueryJobResponse {
    private String queryJobId;
    private OrderStatisticsQueryJobState state;
    private Instant createdAt;
    private Instant finishedAt;
    private String resultToken;
    private String message;

    public static OrderStatisticsQueryJobResponse convert(
            OrderStatisticsQueryJob job
    ) {
        if (job == null) {
            throw new IllegalArgumentException("统计查询任务已过期");
        }
        return OrderStatisticsQueryJobResponse.builder()
                .queryJobId(job.jobId())
                .state(job.state())
                .createdAt(job.createdAt())
                .finishedAt(job.finishedAt())
                .resultToken(job.resultToken())
                .message(job.message())
                .build();
    }
}
