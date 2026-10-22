package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ShopifyCancelCheckResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.service.impl.ShopifyWebsiteStore;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Spy
    private ShopifyAppProperties appProperties = new ShopifyAppProperties();

    @InjectMocks
    private ShopifyOrderSyncExecutor executor;

    @BeforeEach
    void mockEmptyCancelCheck() {
        lenient().when(shopifyOrderSyncService.checkCancelledOrders(any(), any(), any()))
                .thenAnswer(invocation -> new ShopifyCancelCheckResult(invocation.getArgument(1), null));
    }

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

    @Test
    @DisplayName("首次取消检查从商城创建时间开始，并保存处理到的更新时间")
    void shouldCheckCancelledOrdersFromCreateTimeAndPersistCursor() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        LocalDateTime cursor = LocalDateTime.now().minusMinutes(1);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));
        when(shopifyOrderSyncService.checkCancelledOrders(any(), any(), isNull()))
                .thenReturn(new ShopifyCancelCheckResult(cursor, null));

        executor.syncNext();

        verify(shopifyOrderSyncService).checkCancelledOrders(any(), eq(website.getCreateTime()), isNull());
        verify(websiteStore).updateCancelCheckProgress(1L, cursor, null);
    }

    @Test
    @DisplayName("首次取消检查最多回看7天，且5分钟内不重复检查")
    void shouldLimitInitialLookbackAndThrottleCancelCheck() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), true);
        setField(website, "createTime", LocalDateTime.now().minusDays(30));
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        LocalDateTime before = LocalDateTime.now().minusDays(7);
        executor.syncNext();
        executor.syncNext();
        LocalDateTime after = LocalDateTime.now().minusDays(7);

        ArgumentCaptor<LocalDateTime> updatedAtMin = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(shopifyOrderSyncService, times(2)).loadOrders(any(), any(), any(), eq(SyncMode.AUTO));
        verify(shopifyOrderSyncService, times(1)).checkCancelledOrders(any(), updatedAtMin.capture(), isNull());
        assertFalse(updatedAtMin.getValue().isBefore(before));
        assertFalse(updatedAtMin.getValue().isAfter(after));
        // 没有订单更新时保存本次起点，避免下次回看窗口继续滑动
        verify(websiteStore).updateCancelCheckProgress(1L, updatedAtMin.getValue(), null);
    }

    @Test
    @DisplayName("沿用已保存的取消检查游标，无更新时不重复写库")
    void shouldReuseSavedCancelCheckCursor() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        LocalDateTime saved = LocalDateTime.now().minusHours(2);
        website.setLastCancelCheckTime(saved);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));

        executor.syncNext();

        verify(shopifyOrderSyncService).checkCancelledOrders(any(), eq(saved), isNull());
        verify(websiteStore, never()).updateCancelCheckProgress(anyLong(), any(), any());
    }

    @Test
    @DisplayName("取消检查失败不影响新订单同步结果")
    void shouldIgnoreCancelCheckFailure() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult("page2", 0));
        website.setCancelCheckPageInfo("saved-page");
        when(shopifyOrderSyncService.checkCancelledOrders(any(), any(), any())).thenThrow(new RuntimeException("模拟失败"));

        assertEquals(10_000, executor.syncNext());
        verify(websiteStore, never()).updateCancelCheckProgress(anyLong(), any(), any());
        assertEquals("saved-page", website.getCancelCheckPageInfo());
    }

    @Test
    void shouldPersistContinuationEvenWhenTimestampDoesNotChange() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        LocalDateTime saved = LocalDateTime.now().minusHours(2);
        website.setLastCancelCheckTime(saved);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));
        when(shopifyOrderSyncService.checkCancelledOrders(any(), eq(saved), isNull()))
                .thenReturn(new ShopifyCancelCheckResult(saved, "page-11"));

        executor.syncNext();

        verify(websiteStore).updateCancelCheckProgress(1L, saved, "page-11");
    }

    @Test
    void shouldResumePersistedPageAfterRestartAndClearItOnCompletion() {
        ThirdPartyWebsite website = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        LocalDateTime saved = LocalDateTime.now().minusHours(2);
        website.setLastCancelCheckTime(saved);
        website.setCancelCheckPageInfo("page-11");
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));
        when(shopifyOrderSyncService.checkCancelledOrders(any(), eq(saved), eq("page-11")))
                .thenReturn(new ShopifyCancelCheckResult(saved, null));

        executor.syncNext();

        verify(shopifyOrderSyncService).checkCancelledOrders(any(), eq(saved), eq("page-11"));
        verify(websiteStore).updateCancelCheckProgress(1L, saved, null);
    }

    // ==================== 公开应用对账 ====================

    private ThirdPartyWebsite buildPublicAppWebsite(Long id) {
        ThirdPartyWebsite website = buildWebsite(id, LocalDateTime.now().minusMinutes(5), true);
        website.setAccessMode(ShopifyAccessMode.PUBLIC_APP);
        return website;
    }

    @Test
    @DisplayName("公开应用：首次从 installedAt − lookback 对账，推进 lastReconcileTime，不轮询、不单独检查取消")
    void shouldReconcilePublicAppFromInstalledAt() {
        ThirdPartyWebsite website = buildPublicAppWebsite(1L);
        LocalDateTime installedAt = LocalDateTime.now().minusHours(3);
        website.setInstalledAt(installedAt);
        LocalDateTime cursor = installedAt.plusHours(1);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.reconcileOrders(any(), any())).thenReturn(cursor);

        assertEquals(60_000, executor.syncNext());

        verify(shopifyOrderSyncService).reconcileOrders(any(), eq(installedAt.minusMinutes(30)));
        verify(websiteStore).updateReconcileTime(1L, cursor);
        verify(shopifyOrderSyncService, never()).loadOrders(any(), any(), any(), any());
        verify(shopifyOrderSyncService, never()).checkCancelledOrders(any(), any(), any());
    }

    @Test
    @DisplayName("公开应用：沿用 lastReconcileTime，间隔未到不重复对账，配置的间隔与回看生效")
    void shouldRespectReconcileIntervalAndLookback() {
        appProperties.setReconcileIntervalMinutes(15);
        appProperties.setReconcileLookbackMinutes(10);
        ThirdPartyWebsite website = buildPublicAppWebsite(1L);
        LocalDateTime saved = LocalDateTime.now().minusMinutes(20);
        website.setLastReconcileTime(saved);
        website.setInstalledAt(LocalDateTime.now().minusDays(3));
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        // 无订单时服务返回传入的起点（since − lookback），游标不能因此倒退
        when(shopifyOrderSyncService.reconcileOrders(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        executor.syncNext();
        executor.syncNext();

        verify(shopifyOrderSyncService, times(1)).reconcileOrders(any(), eq(saved.minusMinutes(10)));
        verify(websiteStore, never()).updateReconcileTime(anyLong(), any());
    }

    @Test
    @DisplayName("公开应用：从未对账也未记录安装时间时从创建时间开始，并保存起点")
    void shouldFallBackToCreateTimeAndPersistStart() {
        ThirdPartyWebsite website = buildPublicAppWebsite(1L);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(website));
        mockWebsiteDto();
        when(shopifyOrderSyncService.reconcileOrders(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

        executor.syncNext();

        verify(shopifyOrderSyncService).reconcileOrders(any(), eq(website.getCreateTime().minusMinutes(30)));
        verify(websiteStore).updateReconcileTime(1L, website.getCreateTime());
    }

    @Test
    @DisplayName("两种接入方式并存：凭证模式照常轮询与取消检查，公开应用只对账；对账失败不影响其他店铺")
    void shouldDispatchByAccessMode() {
        ThirdPartyWebsite credentials = buildWebsite(1L, LocalDateTime.now().minusMinutes(5), false);
        credentials.setAccessMode(ShopifyAccessMode.CREDENTIALS);
        ThirdPartyWebsite legacy = buildWebsite(2L, LocalDateTime.now().minusMinutes(5), false);
        ThirdPartyWebsite publicApp = buildPublicAppWebsite(3L);
        when(websiteStore.findActiveWebsites()).thenReturn(List.of(credentials, legacy, publicApp));
        mockWebsiteDto();
        when(shopifyOrderSyncService.loadOrders(any(), any(), any(), eq(SyncMode.AUTO))).thenReturn(loadResult(null, 0));
        when(shopifyOrderSyncService.reconcileOrders(any(), any())).thenThrow(new RuntimeException("模拟失败"));

        assertDoesNotThrow(() -> executor.syncNext());

        ArgumentCaptor<ThirdPartyWebsiteDto> loaded = ArgumentCaptor.forClass(ThirdPartyWebsiteDto.class);
        verify(shopifyOrderSyncService, times(2)).loadOrders(loaded.capture(), any(), any(), eq(SyncMode.AUTO));
        assertEquals(java.util.Set.of(1L, 2L), loaded.getAllValues().stream().map(ThirdPartyWebsiteDto::getLongId)
                .collect(java.util.stream.Collectors.toSet()));
        verify(shopifyOrderSyncService, times(2)).checkCancelledOrders(any(), any(), any());
        ArgumentCaptor<ThirdPartyWebsiteDto> reconciled = ArgumentCaptor.forClass(ThirdPartyWebsiteDto.class);
        verify(shopifyOrderSyncService).reconcileOrders(reconciled.capture(), any());
        assertEquals(3L, reconciled.getValue().getLongId());
        verify(websiteStore, never()).updateReconcileTime(anyLong(), any());
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
