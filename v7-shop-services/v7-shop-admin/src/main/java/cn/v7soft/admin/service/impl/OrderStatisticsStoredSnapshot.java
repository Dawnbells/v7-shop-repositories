package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;

import java.time.Instant;

public record OrderStatisticsStoredSnapshot(
        long companyId,
        long userId,
        String resultToken,
        Instant createdAt,
        Instant expiresAt,
        OrderStatisticsResultResponse result
) {
}
