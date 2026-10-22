package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ShopifyCancelCheckResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.service.impl.ShopifyWebsiteStore;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopifyOrderSyncExecutor {

    private static final int MAX_CONCURRENCY = 5;
    private static final long TIMEOUT_PER_WEBSITE_SECONDS = 300;
    private static final long MIN_SYNC_INTERVAL_SECONDS = 60;
    private static final long CANCEL_CHECK_INTERVAL_SECONDS = 300;
    // 首次检查取消状态时最多回看的天数，更早的订单通过手动同步处理
    private static final long CANCEL_CHECK_INITIAL_LOOKBACK_DAYS = 7;

    private final IShopifyOrderSyncService shopifyOrderSyncService;
    private final ShopifyWebsiteStore websiteStore;
    private final ICompanyService companyService;
    private final ShopifyAppProperties appProperties;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    // 各商城上次执行取消检查的服务器时间，仅用于控制频率，重启后会立即检查一次
    private final ConcurrentHashMap<Long, LocalDateTime> lastCancelCheckAt = new ConcurrentHashMap<>();
    // 公开应用店铺上次对账的服务器时间，仅用于控制频率，重启后会立即对账一次
    private final ConcurrentHashMap<Long, LocalDateTime> lastReconcileAt = new ConcurrentHashMap<>();
    private final ExecutorService syncPool = Executors.newFixedThreadPool(MAX_CONCURRENCY,
            r -> {
                Thread t = new Thread(r, "shopify-sync-" + threadCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    public void shutdown() {
        syncPool.shutdownNow();
    }

    /**
     * 扫描所有 VALID 且已认证的 Shopify 商城，按接入方式分流：
     * <p>
     * 凭证模式（accessMode 为 null 或 CREDENTIALS）按条件决定是否同步：
     * - 上次有新订单 → 立即同步
     * - 上次无新订单 → 距上次同步至少间隔 60 秒
     * 每个商城拉取一页新订单，另每 5 分钟检查一次取消状态。
     * <p>
     * 公开应用（PUBLIC_APP）由 webhook 驱动，这里只每 reconcile-interval-minutes 对账一次，不轮询、不单独检查取消。
     * <p>
     * 多个商城并行（最多 5 并发）。返回下次调度延迟（毫秒）：有新订单返回 10s，否则 60s。
     */
    public long syncNext() {
        try {
            TenantContext.silent();
            List<ThirdPartyWebsite> activeWebsites = websiteStore.findActiveWebsites();

            if (activeWebsites.isEmpty()) {
                return 60_000;
            }

            List<ThirdPartyWebsite> credentialWebsites = activeWebsites.stream()
                    .filter(w -> !ShopifyAccessMode.isPublicApp(w.getAccessMode()))
                    .toList();
            List<ThirdPartyWebsite> reconcilable = filterReconcileDueWebsites(activeWebsites.stream()
                    .filter(w -> ShopifyAccessMode.isPublicApp(w.getAccessMode()))
                    .toList());
            List<ThirdPartyWebsite> syncable = filterSyncableWebsites(credentialWebsites);
            if (syncable.isEmpty() && reconcilable.isEmpty()) {
                return credentialWebsites.isEmpty() ? 60_000 : 10_000;
            }

            AtomicBoolean hasNewOrders = new AtomicBoolean(false);
            List<Future<?>> futures = new ArrayList<>(syncable.size() + reconcilable.size());

            for (ThirdPartyWebsite website : reconcilable) {
                futures.add(syncPool.submit(() -> {
                    try {
                        TenantContext.setCurrentTenant(website.getCompanyId(), companyService.companyCached(website.getCompanyId()));
                        reconcileWebsite(website);
                    } catch (Exception e) {
                        log.error("Shopify reconcile website failed: websiteId={}, handle={}",
                                website.getId(), website.getHandle(), e);
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }

            for (ThirdPartyWebsite website : syncable) {
                futures.add(syncPool.submit(() -> {
                    try {
                        TenantContext.setCurrentTenant(website.getCompanyId(), companyService.companyCached(website.getCompanyId()));
                        boolean synced = syncWebsite(website);
                        if (synced) {
                            hasNewOrders.set(true);
                        }
                    } catch (Exception e) {
                        log.error("Shopify auto sync website failed: websiteId={}, handle={}",
                                website.getId(), website.getHandle(), e);
                    }
                    try {
                        checkCancelledOrders(website);
                    } catch (Exception e) {
                        log.error("Shopify cancelled order check failed: websiteId={}, handle={}",
                                website.getId(), website.getHandle(), e);
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_PER_WEBSITE_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.error("Shopify auto sync website timeout after {} seconds", TIMEOUT_PER_WEBSITE_SECONDS, e);
                } catch (Exception e) {
                    log.error("Shopify auto sync website future failed", e);
                }
            }

            return hasNewOrders.get() ? 10_000 : 60_000;
        } finally {
            TenantContext.restore();
        }
    }

    /**
     * 公开应用店铺距上次对账至少间隔 reconcile-interval-minutes；入选即记录本次对账时间（失败也等下一个间隔）
     */
    private List<ThirdPartyWebsite> filterReconcileDueWebsites(List<ThirdPartyWebsite> websites) {
        LocalDateTime now = LocalDateTime.now();
        long intervalSeconds = Math.max(reconcileIntervalMinutes(), 1) * 60L;
        List<ThirdPartyWebsite> due = new ArrayList<>(websites.size());
        for (ThirdPartyWebsite website : websites) {
            LocalDateTime last = lastReconcileAt.get(website.getId());
            if (last == null || Duration.between(last, now).getSeconds() >= intervalSeconds) {
                lastReconcileAt.put(website.getId(), now);
                due.add(website);
            }
        }
        return due;
    }

    /**
     * 对账：since = lastReconcileTime ?: installedAt ?: createTime，从 since − lookback 拉取更新过的订单，
     * 游标推进到返回的最大 updated_at（不早于 since，避免无订单时回看窗口让游标倒退）
     */
    private void reconcileWebsite(ThirdPartyWebsite website) {
        LocalDateTime since = website.getLastReconcileTime() != null ? website.getLastReconcileTime()
                : website.getInstalledAt() != null ? website.getInstalledAt()
                : website.getCreateTime();
        if (since == null) {
            since = LocalDateTime.now();
        }
        ThirdPartyWebsiteDto websiteDto = websiteStore.getDtoById(website.getId());
        LocalDateTime cursor = shopifyOrderSyncService.reconcileOrders(websiteDto, since.minusMinutes(reconcileLookbackMinutes()));
        LocalDateTime next = cursor != null && cursor.isAfter(since) ? cursor : since;
        if (!Objects.equals(next, website.getLastReconcileTime())) {
            websiteStore.updateReconcileTime(website.getId(), next);
        }
    }

    private int reconcileIntervalMinutes() {
        return appProperties != null ? appProperties.getReconcileIntervalMinutes() : 15;
    }

    private int reconcileLookbackMinutes() {
        return appProperties != null ? Math.max(appProperties.getReconcileLookbackMinutes(), 0) : 30;
    }

    private List<ThirdPartyWebsite> filterSyncableWebsites(List<ThirdPartyWebsite> websites) {
        LocalDateTime now = LocalDateTime.now();
        return websites.stream()
                .filter(w -> Boolean.TRUE.equals(w.getLastSyncHasNewOrders())
                        || w.getLastSyncTime() == null
                        || Duration.between(w.getLastSyncTime(), now).getSeconds() >= MIN_SYNC_INTERVAL_SECONDS)
                .toList();
    }

    private boolean syncWebsite(ThirdPartyWebsite website) {
        SyncThirdPartyOrdersRequest request = new SyncThirdPartyOrdersRequest();
        request.setId(String.valueOf(website.getId()));
        LocalDateTime syncFrom = website.getLastSyncOrderTime() != null
                ? website.getLastSyncOrderTime()
                // lastSyncTime records attempts, including empty pages and failed first orders.
                // Until an order succeeds, keep the immutable website creation boundary.
                : website.getCreateTime();
        request.setCreateAtMin(syncFrom);

        ThirdPartyWebsiteDto websiteDto = websiteStore.getDtoById(website.getId());
        ShoplineOrderLoadResult result = shopifyOrderSyncService.loadOrders(websiteDto, request, "", SyncMode.AUTO);

        if (result.getNextPageInfo() != null) {
            return true;
        }
        return result.getCreatedCount() > 0 || websiteStore.lastSyncHasNewOrders(website.getId());
    }

    /**
     * 自动同步只按 created_at 拉新订单，已同步订单后续被取消需按 updated_at 另行检查。
     * 每个商城至少间隔 5 分钟检查一次，游标为已处理到的 Shopify 订单更新时间。
     */
    private void checkCancelledOrders(ThirdPartyWebsite website) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastCheckAt = lastCancelCheckAt.get(website.getId());
        if (lastCheckAt != null && Duration.between(lastCheckAt, now).getSeconds() < CANCEL_CHECK_INTERVAL_SECONDS) {
            return;
        }
        lastCancelCheckAt.put(website.getId(), now);

        LocalDateTime updatedAtMin = website.getLastCancelCheckTime();
        if (updatedAtMin == null) {
            LocalDateTime lookback = now.minusDays(CANCEL_CHECK_INITIAL_LOOKBACK_DAYS);
            updatedAtMin = website.getCreateTime() != null && website.getCreateTime().isAfter(lookback)
                    ? website.getCreateTime() : lookback;
        }
        ThirdPartyWebsiteDto websiteDto = websiteStore.getDtoById(website.getId());
        ShopifyCancelCheckResult result = shopifyOrderSyncService.checkCancelledOrders(
                websiteDto, updatedAtMin, website.getCancelCheckPageInfo());
        if (!Objects.equals(result.cursor(), website.getLastCancelCheckTime())
                || !Objects.equals(result.nextPageInfo(), website.getCancelCheckPageInfo())) {
            websiteStore.updateCancelCheckProgress(website.getId(), result.cursor(), result.nextPageInfo());
        }
    }
}
