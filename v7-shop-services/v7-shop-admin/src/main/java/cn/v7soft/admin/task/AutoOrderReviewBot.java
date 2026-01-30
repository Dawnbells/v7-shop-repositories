package cn.v7soft.admin.task;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import cn.v7soft.admin.task.executor.OrderReviewExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOrderReviewBot {
    private final Environment environment;
    private final ApplicationContext applicationContext; // 注入 Spring 上下文
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void startTask() {
        scheduledNext(10000); // 第一次立即执行
    }

    public void scheduledNext(long delayMillis) {
//        if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
//            log.warn("Skipping order review in dev environment.");
//            return;
//        }

        executorService.schedule(() -> {
            try {
                // 通过 Spring 上下文获取代理类，调用方法
                long nextDelay = applicationContext.getBean(OrderReviewExecutor.class).reviewNext();

                // 如果一切正常，则继续下一轮（2 秒后）
                scheduledNext(nextDelay);
            } catch (Exception e) {
                log.error("⚠️ 自动审单失败, 30秒后继续", e);
                // 出错则等待30秒后继续尝试
                scheduledNext(30000);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
        log.info("✅ AutoOrderReviewBot 已停止线程池");
    }
}
