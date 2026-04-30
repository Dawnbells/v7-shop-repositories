package cn.v7soft.admin.task.executor;

import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShoplineOrderSyncExecutor {

    private static final int MAX_CONCURRENCY = 5;
    private static final long TIMEOUT_PER_WEBSITE_SECONDS = 300;
    private static final long MIN_SYNC_INTERVAL_SECONDS = 60;

    private final IThirdPartyWebsiteService thirdPartyWebsiteService;
    private final ExecutorService syncPool = Executors.newFixedThreadPool(MAX_CONCURRENCY,
            r -> {
                Thread t = new Thread(r, "shopline-sync");
                t.setDaemon(true);
                return t;
            });

    /**
     * 扫描所有 VALID 且已认证的商城，按条件决定是否同步：
     * - 上次有新订单 → 立即同步
     * - 上次无新订单 → 距上次同步至少间隔 60 秒
     * 每个商城拉取一页（最多 100 条）新订单，多个商城并行（最多 5 并发）。
     * 返回下次调度延迟（毫秒）：有新订单返回 10s，否则 60s。
     */
    public long syncNext() {
        try {
            TenantContext.silent();
            List<ThirdPartyWebsite> activeWebsites = thirdPartyWebsiteService.findActiveWebsites();

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
                        TenantContext.silent();
                        boolean synced = syncWebsite(website);
                        if (synced) {
                            hasNewOrders.set(true);
                        }
                    } catch (Exception e) {
                        log.error("Shopline auto sync website failed: websiteId={}, handle={}",
                                website.getId(), website.getHandle(), e);
                    } finally {
                        TenantContext.restore();
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_PER_WEBSITE_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    log.error("Shopline auto sync website timeout after {} seconds", TIMEOUT_PER_WEBSITE_SECONDS, e);
                } catch (Exception e) {
                    log.error("Shopline auto sync website future failed", e);
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
                : (website.getLastSyncTime() != null ? website.getLastSyncTime() : website.getCreateTime());
        request.setCreateAtMin(syncFrom);

        ShoplineOrderLoadResult result = thirdPartyWebsiteService.loadOrders(request, "", SyncMode.AUTO);

        if (result.getNextPageInfo() != null) {
            return true;
        }

        ThirdPartyWebsite refreshed = thirdPartyWebsiteService.getById(website.getId());
        return result.getCreatedCount() > 0 || Boolean.TRUE.equals(refreshed.getLastSyncHasNewOrders());
    }
}
