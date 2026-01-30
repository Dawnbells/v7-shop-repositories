package cn.v7soft.dao.tenant;

import java.util.Objects;

/**
 * 访问数据访问上下文
 */
public abstract class AccessDataRangeContext {
    private static final ThreadLocal<Boolean> disableTemporaries = new ThreadLocal<>();
    public static void silent() {
        disableTemporaries.set(Boolean.TRUE);
    }
    public static void restore() {
        disableTemporaries.remove();
    }

    public static boolean isSilent() {
        return Objects.equals(disableTemporaries.get(), Boolean.TRUE);
    }
}
