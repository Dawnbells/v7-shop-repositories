package cn.v7soft.admin.service.dto;

import cn.v7soft.admin.service.impl.ShopifyTokenService;
import cn.v7soft.dao.enums.CurrencyMode;

import java.time.LocalDateTime;

/**
 * Shopify 公开应用安装票据（一次性，nonce 同时作为 OAuth state），存于 Redis。
 *
 * @param nonce        32 字节随机数的 hex，创建时可为 null，由 {@code ShopifyInstallTicketStore#create} 生成
 * @param type         ADMIN：后台重连；INTENT：后台新增的连接意向；STORE：从 Shopify 后台/App Store 打开应用发起
 * @param handle       店铺 handle（不含 .myshopify.com）；INTENT 票据在落地页确定店铺前为 null
 * @param companyId    ADMIN / INTENT 票据的租户；STORE 票据为 null
 * @param userId       ADMIN / INTENT 票据的发起人（绑定后作为店铺 owner）
 * @param nickName     店铺名称
 * @param currencyMode 币种模式
 * @param websiteId    重连已有记录时的店铺 ID
 * @param returnOrigin ADMIN 票据回跳的后台 origin
 * @param pendingToken STORE 票据在回调后写入的待绑定 token
 * @param createdAt    创建时间
 */
public record ShopifyInstallTicket(String nonce, TicketType type, String handle, Long companyId, Long userId,
                                   String nickName, CurrencyMode currencyMode, Long websiteId, String returnOrigin,
                                   ShopifyTokenService.ExchangedToken pendingToken, LocalDateTime createdAt) {

    public enum TicketType {
        /** 后台「重新连接」已有记录发起 */
        ADMIN,
        /**
         * 后台「连接 Shopify」新增：先签发不含 handle 的意向（nonce 存于 cookie），
         * 落地页拿到 Shopify 的 shop 后换成带 handle 的同类型票据（nonce 作为 OAuth state）
         */
        INTENT,
        /** Shopify 打开 App URL（落地页）发起 */
        STORE
    }

    /**
     * 返回替换了 nonce 的副本
     */
    public ShopifyInstallTicket withNonce(String newNonce) {
        return new ShopifyInstallTicket(newNonce, type, handle, companyId, userId, nickName, currencyMode, websiteId,
                returnOrigin, pendingToken, createdAt);
    }

    /**
     * 返回写入了待绑定 token 的副本
     */
    public ShopifyInstallTicket withPendingToken(ShopifyTokenService.ExchangedToken token) {
        return new ShopifyInstallTicket(nonce, type, handle, companyId, userId, nickName, currencyMode, websiteId,
                returnOrigin, token, createdAt);
    }
}
