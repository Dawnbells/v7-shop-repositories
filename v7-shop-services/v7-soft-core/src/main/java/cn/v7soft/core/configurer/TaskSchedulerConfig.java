package cn.v7soft.core.configurer;

import cn.hutool.core.thread.ThreadUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TaskSchedulerConfig {

    @Primary
    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxPoolSize = corePoolSize * 2;
        int queueCapacity = 500;
        int keepAliveSeconds = 60;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("AsyncExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "orderReviewScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);  // 设置线程池大小为1，保证任务按顺序执行
        scheduler.setThreadNamePrefix("OrderReviewScheduler-");
        return scheduler;
    }

    @Bean(name = "deleteOriginMultimediaFileScheduler")
    public ThreadPoolTaskScheduler deleteOriginMultimediaFileScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);  // 设置线程池大小为1，保证任务按顺序执行
        scheduler.setThreadNamePrefix("DeleteOriginMultimediaScheduler-");
        return scheduler;
    }

}
