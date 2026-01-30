package cn.v7soft.common.utils;

import kotlin.Pair;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Slf4j
public class LocalDateTimeUtils {
    public static Pair<LocalDateTime, LocalDateTime> calculateCheckTimeRange(LocalDateTime createTime, LocalTime dailyOrderCheckStartTime) {
        // 获取订单创建的日期
        LocalDate createDate = createTime.toLocalDate();

        // 获取今天的审单起始时间
        LocalDateTime todayStart = LocalDateTime.of(createDate, dailyOrderCheckStartTime);

        LocalDateTime startOrderCheckTime;
        LocalDateTime endOrderCheckTime;

        // 如果订单创建时间晚于今天的审单开始时间，则该订单属于今天的审单周期
        if (createTime.isAfter(todayStart)) {
            // 开始时间是今天的审单起始时间
            startOrderCheckTime = todayStart;
            // 结束时间是明天的审单起始时间
            endOrderCheckTime = todayStart.plusDays(1);
        } else {
            // 否则，订单属于昨天的审单周期
            // 开始时间是昨天的审单起始时间
            startOrderCheckTime = todayStart.minusDays(1);
            // 结束时间是今天的审单起始时间
            endOrderCheckTime = todayStart;
        }

        log.debug("审单周期开始时间: " + startOrderCheckTime);
        log.debug("审单周期结束时间: " + endOrderCheckTime);
        return new Pair<>(startOrderCheckTime, endOrderCheckTime);
    }

    public static String formatZone8(LocalDateTime localDateTime) {
        // 转换为 OffsetDateTime（东八区 +08:00）
        OffsetDateTime offsetDateTime = localDateTime.atOffset(ZoneOffset.of("+08:00"));
        // 格式化成字符串
        return offsetDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
}
