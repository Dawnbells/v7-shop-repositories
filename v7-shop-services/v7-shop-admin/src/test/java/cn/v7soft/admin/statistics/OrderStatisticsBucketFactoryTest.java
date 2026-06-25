package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsGranularity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatisticsBucketFactoryTest {

    private final OrderStatisticsBucketFactory factory = new OrderStatisticsBucketFactory();

    @Test
    void createsTwentyThreeHourDayAcrossDstSpringForward() {
        List<OrderStatisticsBucket> buckets = factory.create(
                LocalDate.parse("2026-03-08"),
                LocalDate.parse("2026-03-08"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("America/Los_Angeles"),
                Instant.parse("2026-03-10T12:00:00Z")
        );

        OrderStatisticsBucket bucket = buckets.get(0);
        assertThat(Duration.between(bucket.startInstant(), bucket.endInstant()))
                .isEqualTo(Duration.ofHours(23));
        assertThat(bucket.queryStart()).isEqualTo(LocalDateTime.parse("2026-03-08T16:00:00"));
        assertThat(bucket.queryEnd()).isEqualTo(LocalDateTime.parse("2026-03-09T15:00:00"));
    }

    @Test
    void createsTwentyFiveHourDayAcrossDstFallBack() {
        List<OrderStatisticsBucket> buckets = factory.create(
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-01"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("America/Los_Angeles"),
                Instant.parse("2026-11-03T12:00:00Z")
        );

        assertThat(Duration.between(
                buckets.get(0).startInstant(),
                buckets.get(0).endInstant()
        )).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void marksOnlyClippedMonthBucketsAsPartial() {
        List<OrderStatisticsBucket> buckets = factory.create(
                LocalDate.parse("2026-01-15"),
                LocalDate.parse("2026-03-10"),
                OrderStatisticsGranularity.MONTH,
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-04-01T00:00:00Z")
        );

        assertThat(buckets).extracting(OrderStatisticsBucket::key)
                .containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(buckets).extracting(OrderStatisticsBucket::partial)
                .containsExactly(true, false, true);
    }

    @Test
    void cutsCurrentDayOffAtNow() {
        Instant now = Instant.parse("2026-06-24T18:30:00Z");
        List<OrderStatisticsBucket> buckets = factory.create(
                LocalDate.parse("2026-06-24"),
                LocalDate.parse("2026-06-24"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("America/Los_Angeles"),
                now
        );

        assertThat(buckets.get(0).endInstant()).isEqualTo(now);
        assertThat(buckets.get(0).partial()).isTrue();
    }

    @Test
    void rejectsFutureDates() {
        assertThatThrownBy(() -> factory.create(
                LocalDate.parse("2026-06-25"),
                LocalDate.parse("2026-06-25"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("America/Los_Angeles"),
                Instant.parse("2026-06-24T18:30:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未来");
    }

    @Test
    void rejectsDayRangesLongerThanSixtyTwoDays() {
        assertThatThrownBy(() -> factory.create(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-03-04"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-04-01T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("62");
    }

    @Test
    void acceptsExactlySixtyTwoDays() {
        List<OrderStatisticsBucket> buckets = factory.create(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-03-03"),
                OrderStatisticsGranularity.DAY,
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-04-01T00:00:00Z")
        );

        assertThat(buckets).hasSize(62);
    }

    @Test
    void rejectsMonthRangesLongerThanFiveYears() {
        assertThatThrownBy(() -> factory.create(
                LocalDate.parse("2021-01-01"),
                LocalDate.parse("2026-01-02"),
                OrderStatisticsGranularity.MONTH,
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-02-01T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5年");
    }
}
