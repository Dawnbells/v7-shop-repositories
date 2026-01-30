package cn.v7soft.common.utils;

import cn.v7soft.core.enums.ClientResponseEnum;

public class ConvertUtils {
    public static Long parseLong(String value, Object... msg) {
        ClientResponseEnum.PARAMETER_ILLEGAL.isLong(value, msg);
        return Long.valueOf(value.trim());
    }

    public static Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isLong(String value) {
        return parseLongOrNull(value) != null;
    }
}
