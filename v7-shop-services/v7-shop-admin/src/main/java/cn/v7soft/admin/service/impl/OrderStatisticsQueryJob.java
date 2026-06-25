package cn.v7soft.admin.service.impl;

import java.time.Instant;

public record OrderStatisticsQueryJob(
        long companyId,
        long userId,
        String jobId,
        OrderStatisticsQueryJobState state,
        Instant createdAt,
        Instant finishedAt,
        String resultToken,
        String message
) {

    public static OrderStatisticsQueryJob processing(
            long companyId,
            long userId,
            String jobId,
            Instant createdAt
    ) {
        return new OrderStatisticsQueryJob(
                companyId,
                userId,
                jobId,
                OrderStatisticsQueryJobState.PROCESSING,
                createdAt,
                null,
                null,
                null
        );
    }
}
