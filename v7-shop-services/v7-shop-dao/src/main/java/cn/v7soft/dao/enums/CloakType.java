package cn.v7soft.dao.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CloakType {
    /**
     * 管理员落地页
     */
    TICKET_PAGE(PAGE.LAND),
    /**
     * 分享页面
     */
    SHARE_PAGE(PAGE.LAND),
    /**
     * 免检页面
     */
    EXEMPTED_PAGE(PAGE.LAND),
    /**
     * 真实落地页
     */
    LANDING_PAGE(PAGE.LAND),
    /**
     * 爬虫页面
     */
    CRAWLER_PAGE(PAGE.CLOAK),
    /**
     * IP爬虫页面
     */
    IP_CRAWLER_PAGE(PAGE.CLOAK),
    /**
     * 斗篷页面
     */
    CLOAK_PAGE(PAGE.CLOAK),
    /**
     * GOOGLE斗篷页面
     */
    GOOGLE_CLOAK_PAGE(PAGE.CLOAK),
    /**
     * 来自IP提示的斗篷页面
     */
    IP_CLOAK_PAGE(PAGE.CLOAK),
    /**
     * PC设备
     */
    PC_CLOAK_PAGE(PAGE.CLOAK),
    /**
     * 分险页面
     */
    RISK_PAGE(PAGE.RISK),
    /**
     * 来自IP提示的分险
     */
    IP_RISK_PAGE(PAGE.RISK),
    /**
     * 广告平台来源的分险页面
     */
    AD_RISK_PAGE(PAGE.RISK),
    /**
     * 二次拦截分险页面
     */
    LANDING_RISK_PAGE(PAGE.RISK),
    /**
     * 黑名单页面
     */
    BLACKLISTED_PAGE(PAGE.BLACKLISTED);

    private final PAGE page;

    public enum PAGE {
        LAND, // 落地页
        CRAWLER, // 爬虫页
        CLOAK, // 斗篷页
        RISK, // 屏蔽页
        BLACKLISTED // 关闭页
    }
}
