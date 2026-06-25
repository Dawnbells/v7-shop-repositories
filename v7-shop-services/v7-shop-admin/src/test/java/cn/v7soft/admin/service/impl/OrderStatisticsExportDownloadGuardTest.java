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

    private final OrderStatisticsExportDownloadGuard guard =
            new OrderStatisticsExportDownloadGuard(
                    Clock.fixed(
                            Instant.parse("2026-06-25T12:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    @Test
    void rejectsStatisticsExportOlderThanTwentyFourHours() {
        AsyncTask task = AsyncTask.builder()
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .createTime(LocalDateTime.parse("2026-06-24T11:59:59"))
                .build();

        assertThatThrownBy(() -> guard.validate(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void allowsRecentStatisticsExportAndOtherTaskTypes() {
        AsyncTask recent = AsyncTask.builder()
                .taskType(TaskType.ORDER_STATISTICS_EXPORT)
                .createTime(LocalDateTime.parse("2026-06-24T12:00:01"))
                .build();
        AsyncTask other = AsyncTask.builder()
                .taskType(TaskType.ORDER_DOWNLOAD)
                .createTime(LocalDateTime.parse("2020-01-01T00:00:00"))
                .build();

        assertThatCode(() -> guard.validate(recent)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(other)).doesNotThrowAnyException();
    }
}
