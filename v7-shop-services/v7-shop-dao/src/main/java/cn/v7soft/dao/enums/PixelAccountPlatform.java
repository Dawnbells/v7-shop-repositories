package cn.v7soft.dao.enums;

import java.util.regex.Pattern;

public enum PixelAccountPlatform {
    /**
     * meta(facebook)
     */
    META,
    /**
     * GOOGLE
     */
    GOOGLE,
    /**
     * tiktok
     */
    TIKTOK,
    /**
     * taboola
     */
    TABOOLA,
    /**
     * bigo
     */
    BIGO,
    /**
     * google tag manager（容器代码注入，事件推送至 dataLayer）
     */
    GTM,
    /**
     * embed html code
     */
    EMBED;

    /**
     * GTM 容器 ID 的合法格式：GTM- 前缀 + 大写字母/数字。
     */
    private static final Pattern GTM_CONTAINER_ID = Pattern.compile("^GTM-[A-Z0-9]+$");

    /**
     * 规范化并校验 GTM 容器 ID。
     * <p>接受 {@code GTM-XXXXXX}，也兼容用户只填 {@code XXXXXX}（自动补 {@code GTM-} 前缀），
     * 并统一为大写。仅当最终匹配 {@code ^GTM-[A-Z0-9]+$} 时返回规范化结果，否则返回 {@code null}。
     *
     * @param pixelId 原始输入
     * @return 规范化后的容器 ID；非法时返回 {@code null}
     */
    public static String normalizeGtmContainerId(String pixelId) {
        if (pixelId == null) {
            return null;
        }
        String normalized = pixelId.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.startsWith("GTM-")) {
            normalized = "GTM-" + normalized;
        }
        return GTM_CONTAINER_ID.matcher(normalized).matches() ? normalized : null;
    }
}
