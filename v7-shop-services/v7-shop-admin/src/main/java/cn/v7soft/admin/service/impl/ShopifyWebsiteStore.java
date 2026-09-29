package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shopify 商城的数据库读写，独立成 Bean 以保证事务生效
 */
@Service
@RequiredArgsConstructor
public class ShopifyWebsiteStore {
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
    public void markWebsiteAuthError(Long websiteId, String message) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
        website.setAuthMessage(message);
        website.setStatus(StatusEnum.INVALID);
        repository.saveAndFlush(website);
    }

    private ThirdPartyWebsite getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ClientResponseEnum.PARAMETER_ILLEGAL.newException("参数错：" + id));
    }
}
