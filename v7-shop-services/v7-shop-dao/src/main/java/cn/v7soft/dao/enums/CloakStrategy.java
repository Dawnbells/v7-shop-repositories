package cn.v7soft.dao.enums;

/**
 * 斗篷策略
 */
public enum CloakStrategy {
    /**
     * 无策略
     */
    NONE,
    /**
     * 全部屏蔽策略-除了ticket
     */
    PHANTOM_ISOLATION,
    /**
     * 默认
     */
    DEFAULT,
    /**
     * 谷歌(严格)
     */
    GOOGLE_STRICT,
    /**
     * 谷歌(常规)
     */
    GOOGLE_NORMAL,
    /**
     * 谷歌(宽松)
     */
    GOOGLE_LENIENT,
}
