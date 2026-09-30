package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.service.impl.ShopifyWebsiteStore;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyOrderSyncExecutorTest {

    @Mock
    private IShopifyOrderSyncService shopifyOrderSyncService;
    @Mock
    private ShopifyWebsiteStore websiteStore;
    @Mock
    private ICompanyService companyService;

    @InjectMocks
    private ShopifyOrderSyncExecutor executor;

    @AfterEach
    void tearDown() {
        executor.shutdown();
        TenantContext.clear();
    }

    private ThirdPartyWebsite buildWebsite(Long id, LocalDateTime lastSyncTime, Boolean lastSyncHasNewOrders) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                .companyId(id + 100L)
                .nickName("TestShop")
                .handle("test-shop")
                .token("token")
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .lastSyncTime(lastSyncTime)
                .lastSyncHasNewOrders(lastSyncHasNewOrders)
                .build();
        setField(website, "id", id);
        setField(website, "createTime", LocalDateTime.now().minusDays(1));
        return website;
    }

    private void mockWebsiteDto() {
        when(websiteStore.getDtoById(anyLong())).thenAnswer(invocation ->
                ThirdPartyWebsiteDto.builder().id(String.valueOf((Long) invocation.getArgument(0))).build());
    }

    @Test
    void shouldKeepInitialBoundaryAcrossFailedAttempts() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), true);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO)))
                .thenReturn(ShoplineOrderLoadResult.builder().failedCount(1).build());

        executor.syncNext();
        website.setLastSyncTime(LocalDateTime.now());
        executor.syncNext();

        ArgumentCaptor<SyncThirdPartyOrdersRequest> requests = ArgumentCaptor.forClass(SyncThirdPartyOrdersRequest.class);
        verify(shopifyOrderSyncService, times(2)).loadOrders(any(), requests.capture(), any(), eq(SyncMode.AUTO));
        assertEquals(website.getCreateTime(), requests.getAllValues().get(0).getCreateAtMin());
        assertEquals(website.getCreateTime(), requests.getAllValues().get(1).getCreateAtMin());
    }

    @Test
    void shouldUseSuccessfulOrderBoundaryAndScopeEachWebsite() {
        ThirdPartyWebsite first = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        ThirdPartyWebsite second = buildWebsite(2L, LocalDateTime.now().minusMinutes(5), false);
        LocalDateTime cursor = LocalDateTime.now().minusHours(1);
        first.setLastSyncOrderTime(cursor);
        record Observation(Long companyId, boolean silent, LocalDateTime from) {}
        Map<Long, Observation> beforeDto = new ConcurrentHashMap<>();
        Map<Long, Observation> beforeLoad = new ConcurrentHashMap<>();
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(first, second));
        when(websiteStore.getDtoById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            beforeDto.put(id, new Observation(TenantContext.getCurrentTenant(), TenantContext.isSilent(), null));
            return ThirdPartyWebsiteDto.builder().id(id.toString()).build();
        });
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO)))
                .thenAnswer(invocation -> {
                    ThirdPartyWebsiteDto dto = invocation.getArgument(0);
                    SyncThirdPartyOrdersRequest request = invocation.getArgument(1);
                    beforeLoad.put(dto.getLongId(), new Observation(TenantContext.getCurrentTenant(),
                            TenantContext.isSilent(), request.getCreateAtMin()));
                    return loadResult(null, 0);
                });

        executor.syncNext();
        verify(shopifyOrderSyncService, times(2)).loadOrders(any(), any(), any(), eq(SyncMode.AUTO));
        for (long id : List.of(1L, 2L)) {
            assertEquals(new Observation(id + 100L, false, null), beforeDto.get(id));
            assertEquals(new Observation(id + 100L, false, id == 1L ? cursor : second.getCreateTime()), beforeLoad.get(id));
        }
    }

    @Test
    @DisplayName("没有可同步的商城时应返回60秒延迟")
    void shouldReturn60sWhenNoWebsites() {
        when(websiteStore.findActiveWebsites()).thenReturn(Collections.emptyList());

        assertEquals(60_000, executor.syncNext());
    }

    @Test
    @DisplayName("上次无新订单且距上次同步不足60秒应跳过")
    void shouldSkipWhenNoNewOrdersAndTooSoon() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusSeconds(30), false);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));

        long delay = executor.syncNext();

        verify(shopifyOrderSyncService, never()).loadOrders(any(), any(), any(), any());
        assertEquals(10_000, delay);
    }

    @Test
    @DisplayName("有更多页时应返回10秒延迟")
    void shouldReturn10sWhenHasMorePages() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult("page2", 0));

        assertEquals(10_000, executor.syncNext());
    }

    @Test
    @DisplayName("无更多页且无新订单时应返回60秒延迟")
    void shouldReturn60sWhenNoMorePages() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), eq(""), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        assertEquals(60_000, executor.syncNext());
    }

    @Test
    @DisplayName("单个商城失败不影响其他商城")
    void shouldContinueAfterSingleWebsiteFailure() {
        ThirdPartyWebsite website1 = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        ThirdPartyWebsite website2 = buildWebsite(2L, LocalDateTime.now().minusMinutes(5), false);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website1, website2));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO)))
                .thenThrow(new RuntimeException("模拟失败"))
                .thenReturn(loadResult(null, 0));

        assertDoesNotThrow(() -> executor.syncNext());
        verify(shopifyOrderSyncService, times(2)).loadOrders(any(), any(), any(), eq(SyncMode.AUTO));
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
