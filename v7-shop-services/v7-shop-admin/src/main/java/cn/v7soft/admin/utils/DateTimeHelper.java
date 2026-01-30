package cn.v7soft.admin.utils;

import cn.hutool.core.date.LocalDateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DateTimeHelper {
    public static LocalDateTime parseLocalDateTime(String str) {
        String[] formats = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "MM-dd",
                "HH:mm:ss",
        };
        for (String format : formats) {
            LocalDateTime localDateTime = parseSilent(str, format);
            if (localDateTime != null) {
                return localDateTime;
            }
        }
        return null;
    }
    public static LocalDate parseLocalDate(String str) {
        LocalDateTime localDateTime = parseLocalDateTime(str);
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.toLocalDate();
    }

    private static LocalDateTime parseSilent(String str, String format) {
        try {
            return LocalDateTimeUtil.parse(str, format);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 手动计算天时分秒格式（可定制输出）
     *
     * @param seconds 秒数
     * @return 格式化字符串
     */
    public static String formatSecondsManual(Long seconds) {
        if (seconds == null || seconds < 0) {
            return null;
        }
        long day = seconds / (24 * 3600);
        long hour = (seconds % (24 * 3600)) / 3600;
        long minute = (seconds % 3600) / 60;
        long second = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (day > 0) {
            sb.append(day).append("天");
        }
        if (hour > 0) {
            sb.append(hour).append("时");
        }
        if (minute > 0) {
            sb.append(minute).append("分");
        }
        if (second > 0 || sb.length() == 0) {
            sb.append(second).append("秒");
        }

        return sb.toString();
    }
}
