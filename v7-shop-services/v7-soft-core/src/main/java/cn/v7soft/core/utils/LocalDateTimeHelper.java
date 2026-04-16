package cn.v7soft.core.utils;

import java.time.Duration;
import java.time.LocalDateTime;

public class LocalDateTimeHelper {
    /**
     * 判断某个时间对于当前时间来说是否已经过期
     *
     * @param localDateTime 判断的时间
     * @param effectiveTime 有效时间
     * @return 是否过期，true-已过期，false-未过期
     */
    public static boolean isExpired(LocalDateTime localDateTime, Duration effectiveTime) {
        return localDateTime.isBefore(LocalDateTime.now().minus(effectiveTime));
    }
}
