package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class OrderStatisticsExportDownloadGuard {

    private static final Duration DOWNLOAD_TTL = Duration.ofHours(24);
    // 数据库 createTime 为无时区 LocalDateTime，基准为 Asia/Shanghai；比较 now 须用同一基准
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;

    public OrderStatisticsExportDownloadGuard() {
        this(Clock.systemUTC());
    }

    OrderStatisticsExportDownloadGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * 校验统计导出文件是否允许下载。仅对 {@code ORDER_STATISTICS_EXPORT} 生效，其它任务类型直接放行。
     *
     * @param task          待下载任务
     * @param currentUserId 当前登录用户 id（未登录为 null）
     * @param ownerId       任务归属用户 id（由 repository 投影获取，避免触发懒加载）
     */
    public void validate(AsyncTask task, Long currentUserId, Long ownerId) {
        if (task.getTaskType() != TaskType.ORDER_STATISTICS_EXPORT) {
            return;
        }
        // 归属校验置于 TTL 之前：仅本人可下载，未登录或非本人一律拒绝。
        // 上层控制器统一转为 404，非 owner 连"是否过期/是否存在"都无从探测。
        if (currentUserId == null || !Objects.equals(currentUserId, ownerId)) {
            throw forbidden();
        }
        LocalDateTime createTime = task.getCreateTime();
        if (createTime == null) {
            throw expired();
        }
        LocalDateTime expiresAt = createTime.plus(DOWNLOAD_TTL);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), DATABASE_ZONE);
        if (!expiresAt.isAfter(now)) {
            throw expired();
        }
    }

    private IllegalArgumentException expired() {
        return new IllegalArgumentException("统计导出文件已过期，请重新导出");
    }

    private IllegalArgumentException forbidden() {
        return new IllegalArgumentException("无权下载该统计导出文件");
    }
}
