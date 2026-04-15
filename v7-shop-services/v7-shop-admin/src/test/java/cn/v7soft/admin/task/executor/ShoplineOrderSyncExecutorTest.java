package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.service.IThirdPartyWebsiteService;
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

    private ThirdPartyWebsite buildWebsite(Long id, LocalDateTime lastSyncTime) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                .nickName("TestShop")
                .handle("test-shop")
                .token("token")
                .appKey("key")
                .appSecret("secret")
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPLINE)
                .syncEnabled(true)
                .lastSyncTime(lastSyncTime)
                .build();
        setId(website, id);
        setField(website, "createTime", LocalDateTime.now().minusDays(1));
        return website;
    }

    @Test
    @DisplayName("没有可同步的商城时应返回60秒延迟")
    void shouldReturn60sWhenNoWebsites() {
        when(thirdPartyWebsiteService.findSyncEnabledWebsites()).thenReturn(Collections.emptyList());

        long delay = executor.syncNext();

        assertEquals(60_000, delay);
    }

    @Test
    @DisplayName("lastSyncTime为null时应使用createTime作为起点正常同步")
    void shouldUseCreateTimeWhenLastSyncTimeIsNull() {
        ThirdPartyWebsite website = buildWebsite(1L, null);
        when(thirdPartyWebsiteService.findSyncEnabledWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(true))).thenReturn(null);

        long delay = executor.syncNext();

        verify(thirdPartyWebsiteService, times(1)).loadOrders(any(), any(), eq(true));
        assertEquals(60_000, delay);
    }

    @Test
    @DisplayName("有新订单时应返回10秒延迟")
    void shouldReturn10sWhenHasNewOrders() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusHours(1));
        when(thirdPartyWebsiteService.findSyncEnabledWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(true))).thenReturn("page2");
        when(thirdPartyWebsiteService.loadOrders(any(), eq("page2"), eq(true))).thenReturn(null);

        long delay = executor.syncNext();

        assertEquals(10_000, delay);
        verify(thirdPartyWebsiteService, times(2)).loadOrders(any(), any(), eq(true));
    }

    @Test
    @DisplayName("单个商城失败不影响其他商城")
    void shouldContinueAfterSingleWebsiteFailure() {
        ThirdPartyWebsite website1 = buildWebsite(1L, LocalDateTime.now().minusHours(1));
        ThirdPartyWebsite website2 = buildWebsite(2L, LocalDateTime.now().minusHours(1));
        when(thirdPartyWebsiteService.findSyncEnabledWebsites()).thenReturn(List.of(website1, website2));
        when(thirdPartyWebsiteService.loadOrders(any(), any(), eq(true)))
                .thenThrow(new RuntimeException("模拟失败"))
                .thenReturn(null);

        assertDoesNotThrow(() -> executor.syncNext());
        verify(thirdPartyWebsiteService, times(2)).loadOrders(any(), any(), eq(true));
    }

    @Test
    @DisplayName("多页分页应循环直到返回null")
    void shouldPaginateThroughAllPages() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusHours(1));
        when(thirdPartyWebsiteService.findSyncEnabledWebsites()).thenReturn(List.of(website));
        when(thirdPartyWebsiteService.loadOrders(any(), eq(""), eq(true))).thenReturn("page2");
        when(thirdPartyWebsiteService.loadOrders(any(), eq("page2"), eq(true))).thenReturn("page3");
        when(thirdPartyWebsiteService.loadOrders(any(), eq("page3"), eq(true))).thenReturn(null);

        executor.syncNext();

        verify(thirdPartyWebsiteService, times(3)).loadOrders(any(), any(), eq(true));
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
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
