package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Shopify 商城的数据库读写，独立成 Bean 以保证事务生效
 */
@Service
@RequiredArgsConstructor
public class ShopifyWebsiteStore {
    /** 断开/清除凭证时写入的 token 占位前缀（token 列 NOT NULL + 唯一） */
    public static final String DISCONNECTED_TOKEN_PREFIX = "DISCONNECTED:";
    private static final String READ_ALL_ORDERS_SCOPE = "read_all_orders";
    private static final String SHOP_DOMAIN_SUFFIX = ".myshopify.com";

    private final ThirdPartyWebsiteRepository repository;

    @Transactional
    public ThirdPartyWebsiteDto getDtoById(Long id) {
        return ThirdPartyWebsiteDto.convert(getById(id));
    }

    /**
     * 查询所有状态为 VALID 且已认证的 Shopify 商城
     */
    public List<ThirdPartyWebsite> findActiveWebsites() {
        return repository.findByStatusAndAuthStatusAndWebsiteType(StatusEnum.VALID, ThirdPartyAuthStatusEnum.AUTHED, WebsiteTypeEnum.SHOPIFY);
    }

    public boolean lastSyncHasNewOrders(Long id) {
        return Boolean.TRUE.equals(getById(id).getLastSyncHasNewOrders());
    }

    @Transactional
    public void updateToken(Long websiteId, String token, LocalDateTime expiresAt) {
        repository.updateToken(websiteId, token, expiresAt);
    }

    @Transactional
    public void updateLastSyncInfo(Long websiteId, ShoplineOrderLoadResult result) {
        boolean hasNewOrders = result != null && result.getCreatedCount() > 0;
        LocalDateTime orderTime = result != null ? result.getCursorOrderTime() : null;
        String lastOrderId = result != null ? result.getCursorOrderId() : null;
        repository.updateSyncInfo(websiteId, LocalDateTime.now(), hasNewOrders, orderTime, lastOrderId);
    }

    @Transactional
    public void updateCancelCheckProgress(Long websiteId, LocalDateTime checkTime, String pageInfo) {
        repository.updateCancelCheckProgress(websiteId, checkTime, pageInfo);
    }

    @Transactional
    public void markWebsiteAuthError(Long websiteId, String message) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
        website.setAuthMessage(message);
        website.setStatus(StatusEnum.INVALID);
        repository.saveAndFlush(website);
    }

    // ==================== 公开应用 ====================

    /**
     * 查询所有 VALID、已认证、通过公开应用接入的 Shopify 商城
     */
    public List<ThirdPartyWebsite> findActivePublicAppWebsites() {
        return repository.findByStatusAndAuthStatusAndWebsiteTypeAndAccessMode(StatusEnum.VALID,
                ThirdPartyAuthStatusEnum.AUTHED, WebsiteTypeEnum.SHOPIFY, ShopifyAccessMode.PUBLIC_APP);
    }

    /**
     * 按 Shopify 店铺域名（xxx.myshopify.com）跨租户查询店铺。
     * <p>
     * 本方法内临时开启 {@link TenantContext#silent()}，结束后恢复调用前的状态；
     * 租户在会话打开时解析，调用方不要在已开启的事务内调用。
     */
    public Optional<ThirdPartyWebsite> findByShopDomain(String shopDomain) {
        String handle = handleOfShopDomain(shopDomain);
        if (StrUtil.isBlank(handle)) {
            return Optional.empty();
        }
        boolean wasSilent = TenantContext.isSilent();
        TenantContext.silent();
        try {
            return repository.findByHandle(handle);
        } finally {
            if (!wasSilent) {
                TenantContext.restore();
            }
        }
    }

    /**
     * 更新公开应用的 access/refresh token，同时更新 grantedScopes、hasAllOrdersScope（scope 为空时保留原值）
     */
    @Transactional
    public void updatePublicAppTokens(Long id, ShopifyTokenService.ExchangedToken token) {
        String scope = token.scope();
        Boolean hasAllOrdersScope = scope == null ? null : hasAllOrdersScope(scope);
        repository.updatePublicAppTokens(id, token.accessToken(), token.accessExpiresAt(), token.refreshToken(),
                token.refreshExpiresAt(), scope, hasAllOrdersScope);
    }

    /**
     * 商家卸载应用：authStatus=DISCONNECTED、token 写占位值并立即过期、清空 refresh token、status=INVALID、记录卸载时间
     */
    @Transactional
    public void markDisconnected(Long id) {
        repository.markDisconnected(id, ThirdPartyAuthStatusEnum.DISCONNECTED, disconnectedToken(id),
                StatusEnum.INVALID, LocalDateTime.now());
    }

    @Transactional
    public void updateReconcileTime(Long id, LocalDateTime time) {
        repository.updateReconcileTime(id, time);
    }

    /**
     * shop/redact：token 写占位值并立即过期，清空 refresh token 与 client secret
     */
    @Transactional
    public void clearCredentials(Long id) {
        repository.clearCredentials(id, disconnectedToken(id), LocalDateTime.now());
    }

    /**
     * scope 原文（逗号分隔）是否包含 read_all_orders
     */
    static boolean hasAllOrdersScope(String scope) {
        if (StrUtil.isBlank(scope)) {
            return false;
        }
        return StrUtil.split(scope, ',', true, true).contains(READ_ALL_ORDERS_SCOPE);
    }

    static String disconnectedToken(Long id) {
        return DISCONNECTED_TOKEN_PREFIX + id;
    }

    private static String handleOfShopDomain(String shopDomain) {
        if (StrUtil.isBlank(shopDomain)) {
            return null;
        }
        String value = shopDomain.trim().toLowerCase(Locale.ROOT);
        return StrUtil.removeSuffix(value, SHOP_DOMAIN_SUFFIX);
    }

    private ThirdPartyWebsite getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("参数错：" + id));
    }
}
