package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoplineOrderSyncExecutorTest {

    @Mock
    private IThirdPartyWebsiteService thirdPartyWebsiteService;

    @InjectMocks
    private ShoplineOrderSyncExecutor executor;

    private ThirdPartyWebsite buildWebsite(Long id, LocalDateTime lastSyncTime, Boolean lastSyncHasNewOrders) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                .nickName("TestShop")
                .handle("test-shop")
                .token("token")
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPLINE)
                .lastSyncTime(lastSyncTime)
                .lastSyncHasNewOrders(lastSyncHasNewOrders)
                .build();
        setId(website, id);
        setField(website, "createTime", LocalDateTime.now().minusDays(1));
        return website;
    }

    @Test
    @DisplayName("没有可同步的商城时应返回60秒延迟")
    void shouldReturn60sWhenNoWebsites() {
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(Collections.emptyList());

        long delay = executor.syncNext();

        assertEquals(60_000, delay);
    }

    @Test
    @DisplayName("上次有新订单的商城应立即同步")
    void shouldSyncImmediatelyWhenLastSyncHadNewOrders() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now(), true);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        executor.syncNext();

        verify(thirdPartyWebsiteService, times(1)).loadOrders(any(), any(), eq(SyncMode.AUTO));
    }

    @Test
    @DisplayName("上次无新订单且距上次同步不足60秒应跳过")
    void shouldSkipWhenNoNewOrdersAndTooSoon() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusSeconds(30), false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));

        long delay = executor.syncNext();

        verify(thirdPartyWebsiteService, never()).loadOrders(any(), any(), any());
        assertEquals(10_000, delay);
    }

    @Test
    @DisplayName("上次无新订单但距上次同步超过60秒应同步")
    void shouldSyncWhenNoNewOrdersButIntervalExceeded() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusSeconds(120), false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        executor.syncNext();

        verify(thirdPartyWebsiteService, times(1)).loadOrders(any(), any(), eq(SyncMode.AUTO));
    }

    @Test
    @DisplayName("lastSyncTime为null时应立即同步")
    void shouldSyncWhenLastSyncTimeIsNull() {
        ThirdPartyWebsite website = buildWebsite(1L, null, false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        executor.syncNext();

        verify(thirdPartyWebsiteService, times(1)).loadOrders(any(), any(), eq(SyncMode.AUTO));
    }

    @Test
    @DisplayName("有更多页时应返回10秒延迟")
    void shouldReturn10sWhenHasMorePages() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult("page2", 0));

        long delay = executor.syncNext();

        assertEquals(10_000, delay);
    }

    @Test
    @DisplayName("无更多页时应返回60秒延迟")
    void shouldReturn60sWhenNoMorePages() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        long delay = executor.syncNext();

        assertEquals(60_000, delay);
    }

    @Test
    @DisplayName("单个商城失败不影响其他商城")
    void shouldContinueAfterSingleWebsiteFailure() {
        ThirdPartyWebsite website1 = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        ThirdPartyWebsite website2 = buildWebsite(2L, LocalDateTime.now().minusMinutes(5), false);
        when(thirdPartyWebsiteService.findActiveWebsites()).thenReturn(List.of(website1, website2));
        when(thirdPartyWebsiteService.loadOrders(any(), any(), eq(SyncMode.AUTO)))
                .thenThrow(new RuntimeException("模拟失败"))
                .thenReturn(loadResult(null, 0));

        assertDoesNotThrow(() -> executor.syncNext());
        verify(thirdPartyWebsiteService, times(2)).loadOrders(any(), any(), eq(SyncMode.AUTO));
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private ShoplineOrderLoadResult loadResult(String nextPageInfo, int createdCount) {
        return ShoplineOrderLoadResult.builder()
                .nextPageInfo(nextPageInfo)
                .createdCount(createdCount)
                .build();
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(entity, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
    }
}
