package cn.v7soft.dao.enums;

/**
 * 第三方商城状态
 */
public enum ThirdPartyAuthStatusEnum {
    /**
     * 初始状态
     */
    INIT,
    /**
     * 已绑定
     */
    AUTHED,
    /**
     * 绑定失败
     */
    ERROR,
    /**
     * 店铺已封号（第三方平台返回 Store is frozen，店铺会被自动禁用）
     */
    FROZEN
}
