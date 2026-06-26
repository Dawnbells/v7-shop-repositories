package cn.v7soft.admin.service.impl;

import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.enums.TaskType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatisticsExportDownloadGuardTest {

    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;

    private final OrderStatisticsExportDownloadGuard guard =
            new OrderStatisticsExportDownloadGuard(
                    Clock.fixed(
                            Instant.parse("2026-06-25T12:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    private AsyncTask statisticsTask(String createTime) {
        return AsyncTask.builder()
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .createTime(LocalDateTime.parse(createTime))
                .build();
    }

    @Test
    void allowsOwnerWithinTwentyFourHours() {
        AsyncTask task = statisticsTask("2026-06-24T12:00:01");

        assertThatCode(() -> guard.validate(task, OWNER_ID, OWNER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonOwner() {
        AsyncTask task = statisticsTask("2026-06-24T12:00:01");

        assertThatThrownBy(() -> guard.validate(task, OTHER_USER_ID, OWNER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void rejectsAnonymousDownloadWhenCurrentUserIsNull() {
        AsyncTask task = statisticsTask("2026-06-24T12:00:01");

        assertThatThrownBy(() -> guard.validate(task, null, OWNER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void rejectsStatisticsExportOlderThanTwentyFourHours() {
        AsyncTask task = statisticsTask("2026-06-24T11:59:59");

        assertThatThrownBy(() -> guard.validate(task, OWNER_ID, OWNER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void nonOwnerOnExpiredTaskReportsForbiddenNotExpired() {
        // 非 owner 应优先报"无权"，不泄露文件是否已过期/是否存在（无 oracle）
        AsyncTask task = statisticsTask("2026-06-24T11:59:59");

        assertThatThrownBy(() -> guard.validate(task, OTHER_USER_ID, OWNER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void allowsOtherTaskTypesWithoutOwnerOrLogin() {
        // 其它任务类型早退，传 null 也放行 —— 证明对订单下载等其它下载零影响
        AsyncTask other = AsyncTask.builder()
                .taskType(TaskType.ORDER_DOWNLOAD)
                .createTime(LocalDateTime.parse("2020-01-01T00:00:00"))
                .build();

        assertThatCode(() -> guard.validate(other, null, null))
                .doesNotThrowAnyException();
    }
}
