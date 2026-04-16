package cn.v7soft.core.configurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "certificateRequestAsyncExecutor")
    public Executor certificateRequestAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("CertificateRequest-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "syncMultimediaFileSyncExecutor")
    public Executor syncMultimediaFileSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("MultimediaFileSync-");
        executor.initialize();
        return executor;
    }
}
