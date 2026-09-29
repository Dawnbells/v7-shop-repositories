package cn.v7soft.admin.task;

import cn.v7soft.admin.task.executor.ShopifyOrderSyncExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncBot {
    private final ApplicationContext applicationContext;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startTask() {
        scheduleNext(30_000);
    }

    public void scheduleNext(long delayMillis) {
        executorService.schedule(() -> {
            try {
                long nextDelay = applicationContext.getBean(ShopifyOrderSyncExecutor.class).syncNext();
                scheduleNext(nextDelay);
            } catch (Exception e) {
                log.error("Shopify sync bot schedule failed", e);
                scheduleNext(30_000);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
