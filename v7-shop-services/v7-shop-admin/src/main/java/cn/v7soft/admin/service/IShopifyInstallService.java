package cn.v7soft.admin.service;

import cn.v7soft.admin.service.impl.ShopifyTokenService;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import lombok.Getter;

import java.util.Map;

/**
 * Shopify 公开应用的安装与绑定：授权 URL、OAuth 回调、落地页、绑定、创建/恢复店铺。
 * 设计文档：docs/features/shopify-public-app/ARCH.md §5.1、§5.2、§6.3、§9
 */
public interface IShopifyInstallService {

    /**
     * 结果页与后台共用的错误码（ARCH.md §5.2）
     */
    enum ErrorCode {
        INVALID_STATE,
        INVALID_HMAC,
        INVALID_SHOP,
        TOKEN_EXCHANGE_FAILED,
        HANDLE_TAKEN,
        TICKET_EXPIRED,
        UNKNOWN_HOST,
        APP_NOT_CONFIGURED
    }

    /**
     * 安装流程中可映射为错误码的失败
     */
    @Getter
    class InstallException extends RuntimeException {
        private final ErrorCode code;

        public InstallException(ErrorCode code) {
            super(code.name());
            this.code = code;
        }
    }

    /**
     * 连接意向 cookie 的处理方式
     */
    enum IntentCookie {
        /** 不改动 */
        KEEP,
        /** 写入本次的意向 nonce */
        SET,
        /** 清除 */
        CLEAR
    }

    /**
     * 302 目标地址与连接意向 cookie 的处理方式
     * @param intent SET 时写入 cookie 的意向 nonce，其他情况为 null
     */
    record Redirect(String location, IntentCookie intentCookie, String intent) {
        public static Redirect of(String location) {
            return new Redirect(location, IntentCookie.KEEP, null);
        }
    }

    /**
     * 后台「连接 Shopify」，校验回跳来源后：
     * <ul>
     *     <li>新增（id 为空）：签发不含 handle 的 INTENT 票据，返回中转地址 {@code {app-host}/shopify-app/connect?intent={nonce}}，
     *     店铺由商家在 Shopify 中选择，不要求手填店铺标识（App Store 要求 2.3.1）</li>
     *     <li>重连（带 id）：handle 取自原记录，签发 ADMIN 票据，返回 Shopify 授权 URL</li>
     * </ul>
     * @param id                  重连已有记录时的店铺 ID，可空
     * @param nickName            店铺名称，可空（新增时默认取 Shopify 店铺名，重连时默认原名称）
     * @param currencyMode        币种模式，可空（默认 SHOP_MONEY）
     * @param returnOrigin        回调结束后回跳的后台 origin（scheme://host[:port]）
     * @param originTopLevelDomain 请求来源的顶级域名（DomainUtils 解析），必须对应当前租户
     * @param companyId           当前租户
     * @param userId              当前登录人
     */
    String createAdminInstallUrl(Long id, String nickName, CurrencyMode currencyMode,
                                 String returnOrigin, String originTopLevelDomain, Long companyId, Long userId);

    /**
     * 打开中转地址：意向有效 → 写 cookie 并跳 App Store 安装页（未配置 install-url 时跳说明页）；否则跳错误页并清 cookie
     */
    Redirect handleConnect(String intent);

    /**
     * Shopify 打开 App URL：返回 302 目标地址（结果页、租户后台或授权 URL）
     * @param params 原始查询参数（用于 HMAC 校验）
     * @param intent 连接意向 cookie 中的 nonce，可空；有效时以该意向的租户发起授权并消费意向
     */
    Redirect handleLanding(Map<String, String[]> params, String intent);

    /**
     * OAuth 回调：返回 302 目标地址
     * @param params 原始查询参数（code、hmac、shop、state、timestamp、host）
     */
    String handleCallback(Map<String, String[]> params);

    /**
     * 落地页提交后台地址：返回总后台绑定页地址 {@code https://admin.{顶级域名}/#/shopify-bind?bindTicket={nonce}}
     * @throws InstallException TICKET_EXPIRED / UNKNOWN_HOST
     */
    String resolveLanding(String ticket, String adminHost);

    /**
     * 后台绑定页：用 STORE 票据中的待绑定 token 在当前租户下创建/恢复店铺，成功后删除票据
     * @param ticket       STORE 票据 nonce
     * @param nickName     可空，新建时默认 handle，恢复时保留原值
     * @param currencyMode 可空，新建时默认 SHOP_MONEY，恢复时保留原值
     */
    ThirdPartyWebsite bind(String ticket, String nickName, CurrencyMode currencyMode, Long companyId, Long userId);

    /**
     * 创建或恢复公开应用店铺（ARCH.md §6.3），写库后按配置注册 webhook
     * @throws InstallException HANDLE_TAKEN
     */
    ThirdPartyWebsite upsert(String handle, Long companyId, Long userId, String nickName, CurrencyMode currencyMode,
                             ShopifyTokenService.ExchangedToken token);
}
