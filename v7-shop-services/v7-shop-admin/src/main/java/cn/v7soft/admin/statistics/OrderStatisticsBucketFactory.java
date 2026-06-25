package cn.v7soft.admin.statistics;

import cn.v7soft.dao.enums.OrderStatisticsGranularity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OrderStatisticsBucketFactory {

    private static final int MAX_DAY_COUNT = 62;
    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Shanghai");

    public List<OrderStatisticsBucket> create(
            LocalDate startDate,
            LocalDate endDate,
            OrderStatisticsGranularity granularity,
            ZoneId userZone,
            Instant now
    ) {
        validate(startDate, endDate, granularity, userZone, now);

        Instant requestedStart = startDate.atStartOfDay(userZone).toInstant();
        Instant requestedEnd = endDate.plusDays(1).atStartOfDay(userZone).toInstant();
        Instant effectiveEnd = requestedEnd.isAfter(now) ? now : requestedEnd;

        return granularity == OrderStatisticsGranularity.DAY
                ? createDayBuckets(startDate, endDate, userZone, requestedStart, effectiveEnd)
                : createMonthBuckets(startDate, endDate, userZone, requestedStart, effectiveEnd);
    }

    private List<OrderStatisticsBucket> createDayBuckets(
            LocalDate startDate,
            LocalDate endDate,
            ZoneId userZone,
            Instant requestedStart,
            Instant effectiveEnd
    ) {
        List<OrderStatisticsBucket> buckets = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Instant naturalStart = date.atStartOfDay(userZone).toInstant();
            Instant naturalEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();
            addBucket(
                    buckets,
                    date.toString(),
                    naturalStart,
                    naturalEnd,
                    requestedStart,
                    effectiveEnd
            );
        }
        return buckets;
    }

    private List<OrderStatisticsBucket> createMonthBuckets(
            LocalDate startDate,
            LocalDate endDate,
            ZoneId userZone,
            Instant requestedStart,
            Instant effectiveEnd
    ) {
        List<OrderStatisticsBucket> buckets = new ArrayList<>();
        YearMonth firstMonth = YearMonth.from(startDate);
        YearMonth lastMonth = YearMonth.from(endDate);
        for (YearMonth month = firstMonth; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
            Instant naturalStart = month.atDay(1).atStartOfDay(userZone).toInstant();
            Instant naturalEnd = month.plusMonths(1).atDay(1).atStartOfDay(userZone).toInstant();
            addBucket(
                    buckets,
                    month.toString(),
                    naturalStart,
                    naturalEnd,
                    requestedStart,
                    effectiveEnd
            );
        }
        return buckets;
    }

    private void addBucket(
            List<OrderStatisticsBucket> buckets,
            String key,
            Instant naturalStart,
            Instant naturalEnd,
            Instant requestedStart,
            Instant effectiveEnd
    ) {
        Instant bucketStart = naturalStart.isBefore(requestedStart) ? requestedStart : naturalStart;
        Instant bucketEnd = naturalEnd.isAfter(effectiveEnd) ? effectiveEnd : naturalEnd;
        if (!bucketEnd.isAfter(bucketStart)) {
            return;
        }
        buckets.add(new OrderStatisticsBucket(
                key,
                bucketStart,
                bucketEnd,
                LocalDateTime.ofInstant(bucketStart, DATABASE_ZONE),
                LocalDateTime.ofInstant(bucketEnd, DATABASE_ZONE),
                !bucketStart.equals(naturalStart) || !bucketEnd.equals(naturalEnd)
        ));
    }

    private void validate(
            LocalDate startDate,
            LocalDate endDate,
            OrderStatisticsGranularity granularity,
            ZoneId userZone,
            Instant now
    ) {
        Objects.requireNonNull(startDate, "开始日期不能为空");
        Objects.requireNonNull(endDate, "结束日期不能为空");
        Objects.requireNonNull(granularity, "时间粒度不能为空");
        Objects.requireNonNull(userZone, "用户时区不能为空");
        Objects.requireNonNull(now, "当前时间不能为空");

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        LocalDate currentDate = now.atZone(userZone).toLocalDate();
        if (startDate.isAfter(currentDate) || endDate.isAfter(currentDate)) {
            throw new IllegalArgumentException("不能查询未来日期");
        }

        if (granularity == OrderStatisticsGranularity.DAY) {
            long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (dayCount > MAX_DAY_COUNT) {
                throw new IllegalArgumentException("按天统计最多支持62个自然日");
            }
        } else if (endDate.isAfter(startDate.plusYears(5))) {
            throw new IllegalArgumentException("按月统计时间范围不能超过5年");
        }
    }
}
