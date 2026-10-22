package cn.v7soft.dao.enums;

/**
 * Shopify 店铺的接入方式。存量记录为 null，按 {@link #CREDENTIALS} 处理。
 */
public enum ShopifyAccessMode {
    /**
     * 凭证模式：商家在自己组织内创建应用，填 Client ID/Secret，用 client credentials 换 token
     */
    CREDENTIALS,
    /**
     * 公开应用：商家通过 OAuth 安装公开应用，使用可过期的 access token + refresh token
     */
    PUBLIC_APP;

    /**
     * null 视为凭证模式
     */
    public static boolean isPublicApp(ShopifyAccessMode mode) {
        return mode == PUBLIC_APP;
    }
}
