package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.service.impl.ShopifyWebsiteStore;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private final IShopifyOrderSyncService shopifyOrderSyncService;
    private final ShopifyWebsiteStore websiteStore;
    private final ICompanyService companyService;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
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
     * 扫描所有 VALID 且已认证的 Shopify 商城，按条件决定是否同步：
     * - 上次有新订单 → 立即同步
     * - 上次无新订单 → 距上次同步至少间隔 60 秒
     * 每个商城拉取一页（最多 100 条）新订单，多个商城并行（最多 5 并发）。
     * 返回下次调度延迟（毫秒）：有新订单返回 10s，否则 60s。
     */
    public long syncNext() {
        try {
            TenantContext.silent();
            List<ThirdPartyWebsite> activeWebsites = websiteStore.findActiveWebsites();

            if (activeWebsites.isEmpty()) {
                return 60_000;
            }

            List<ThirdPartyWebsite> syncable = filterSyncableWebsites(activeWebsites);
            if (syncable.isEmpty()) {
                return 10_000;
            }

            AtomicBoolean hasNewOrders = new AtomicBoolean(false);
            List<Future<?>> futures = new ArrayList<>(syncable.size());

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
}
