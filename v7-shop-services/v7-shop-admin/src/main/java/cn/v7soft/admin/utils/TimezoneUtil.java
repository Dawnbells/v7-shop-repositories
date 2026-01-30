package cn.v7soft.admin.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import cn.hutool.core.util.StrUtil;

/**
 * <a href="https://data.iana.org/time-zones/tzdata-latest.tar.gz">原始数据来源</a>
 * <a href="https://github.com/shan-shaji/country-code-from-timezone/tree/main">json数据来源</a>
 * <a href="https://github.com/SiddhantAgarwal/GoTimezoneMapper/blob/master/mapper.go">最终标准数据来源</a>
 */
public class TimezoneUtil {

    public static void main(String[] args) {
        String timeZoneId = "Europe/Berlin";
    }

    public static int getStandardTimeOffsetMinute(String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZoneOffset standardOffset = zoneId.getRules().getStandardOffset(Instant.now());
            return standardOffset.getTotalSeconds() / 60;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
