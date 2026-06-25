package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class OrderStatisticsExportDownloadGuard {

    private static final Duration DOWNLOAD_TTL = Duration.ofHours(24);
    private final Clock clock;

    public OrderStatisticsExportDownloadGuard() {
        this(Clock.systemUTC());
    }

    OrderStatisticsExportDownloadGuard(Clock clock) {
        this.clock = clock;
    }

    public void validate(AsyncTask task) {
        if (task.getTaskType() != TaskType.ORDER_STATISTICS_EXPORT) {
            return;
        }
        LocalDateTime createTime = task.getCreateTime();
        if (createTime == null) {
            throw expired();
        }
        LocalDateTime expiresAt = createTime.plus(DOWNLOAD_TTL);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!expiresAt.isAfter(now)) {
            throw expired();
        }
    }

    private IllegalArgumentException expired() {
        return new IllegalArgumentException("统计导出文件已过期，请重新导出");
    }
}
