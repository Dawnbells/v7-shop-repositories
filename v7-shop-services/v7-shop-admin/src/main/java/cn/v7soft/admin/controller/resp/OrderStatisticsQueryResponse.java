package cn.v7soft.admin.controller.resp;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class OrderStatisticsQueryResponse {
    private String state;
    private String queryJobId;
    private String resultToken;
    private Instant snapshotExpiresAt;
    private OrderStatisticsResultResponse result;
    private boolean cached;
    private boolean degraded;
    private String message;
}
