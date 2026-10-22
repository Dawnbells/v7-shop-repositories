package cn.v7soft.admin.service.impl;

import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyWebsiteStoreTest {
    @Mock private ThirdPartyWebsiteRepository repository;

    @InjectMocks
    private ShopifyWebsiteStore store;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("markDisconnected：DISCONNECTED、token 占位 DISCONNECTED:{id}、status=INVALID、记录卸载时间")
    void shouldMarkDisconnected() {
        LocalDateTime before = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        store.markDisconnected(42L);

        verify(repository).markDisconnected(eq(42L), eq(ThirdPartyAuthStatusEnum.DISCONNECTED), eq("DISCONNECTED:42"),
                eq(StatusEnum.INVALID), nowCaptor.capture());
        assertFalse(nowCaptor.getValue().isBefore(before));
    }

    @Test
    @DisplayName("clearCredentials：token 占位并立即过期")
    void shouldClearCredentials() {
        store.clearCredentials(42L);

        verify(repository).clearCredentials(eq(42L), eq("DISCONNECTED:42"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("updatePublicAppTokens：按 scope 计算 hasAllOrdersScope")
    void shouldUpdatePublicAppTokensWithScopeFlag() {
        LocalDateTime accessExpiresAt = LocalDateTime.now().plusHours(1);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(90);

        store.updatePublicAppTokens(1L, new ShopifyTokenService.ExchangedToken("a", accessExpiresAt, "r", refreshExpiresAt,
                "read_orders, read_all_orders,read_products"));
        verify(repository).updatePublicAppTokens(1L, "a", accessExpiresAt, "r", refreshExpiresAt,
                "read_orders, read_all_orders,read_products", true);

        store.updatePublicAppTokens(2L, new ShopifyTokenService.ExchangedToken("a", accessExpiresAt, "r", refreshExpiresAt,
                "read_orders,read_products"));
        verify(repository).updatePublicAppTokens(2L, "a", accessExpiresAt, "r", refreshExpiresAt,
                "read_orders,read_products", false);

        // scope 缺失时保留原值
        store.updatePublicAppTokens(3L, new ShopifyTokenService.ExchangedToken("a", accessExpiresAt, "r", null, null));
        verify(repository).updatePublicAppTokens(3L, "a", accessExpiresAt, "r", null, null, null);
    }

    @Test
    @DisplayName("findActivePublicAppWebsites：VALID + AUTHED + SHOPIFY + PUBLIC_APP")
    void shouldFindActivePublicAppWebsites() {
        when(repository.findByStatusAndAuthStatusAndWebsiteTypeAndAccessMode(StatusEnum.VALID, ThirdPartyAuthStatusEnum.AUTHED,
                WebsiteTypeEnum.SHOPIFY, ShopifyAccessMode.PUBLIC_APP)).thenReturn(List.of());

        assertTrue(store.findActivePublicAppWebsites().isEmpty());
    }

    @Test
    @DisplayName("findByShopDomain：去掉 .myshopify.com 后在 silent 下查询，结束后恢复")
    void shouldFindByShopDomainSilently() {
        ThirdPartyWebsite website = new ThirdPartyWebsite();
        AtomicBoolean silentDuringQuery = new AtomicBoolean(false);
        when(repository.findByHandle("test-shop")).thenAnswer(invocation -> {
            silentDuringQuery.set(TenantContext.isSilent());
            return Optional.of(website);
        });

        assertSame(website, store.findByShopDomain("Test-Shop.myshopify.com").orElseThrow());
        assertTrue(silentDuringQuery.get());
        assertFalse(TenantContext.isSilent());

        assertTrue(store.findByShopDomain(" ").isEmpty());
    }

    @Test
    @DisplayName("findByShopDomain：调用前已 silent 时不恢复")
    void shouldKeepSilentIfAlreadySilent() {
        TenantContext.silent();
        when(repository.findByHandle("test-shop")).thenReturn(Optional.empty());

        assertTrue(store.findByShopDomain("test-shop.myshopify.com").isEmpty());
        assertTrue(TenantContext.isSilent());
    }
}
