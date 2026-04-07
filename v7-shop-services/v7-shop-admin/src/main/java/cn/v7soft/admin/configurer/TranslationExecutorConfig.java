package cn.v7soft.admin.configurer;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;

import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

@Configuration
public class TranslationExecutorConfig {

    @Bean("translationExecutor")
    public ThreadPoolTaskExecutor translationExecutor() {
        int threads = Runtime.getRuntime().availableProcessors() * 2;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(512);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("translation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Gemini API 速率限制：RPM=100, 每分钟最多 100 个请求。
     * 超额请求最多等待 5 分钟获取许可。
     */
    @Bean
    public RateLimiter geminiRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofMinutes(5))
                .build();
        return RateLimiterRegistry.of(config).rateLimiter("gemini");
    }

    /**
     * GeminiTranslateService 内部调用重试（替代原手写 generateContentWithRetry）。
     * 3 次尝试，退避 3s / 9s / 27s。
     */
    @Bean
    public Retry geminiInternalRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(3000, 3.0))
                .retryOnException(TranslationExecutorConfig::isRetryable)
                .build();
        return RetryRegistry.of(config).retry("geminiInternal");
    }

    /**
     * Phase C 即时翻译外层重试，包裹 Raw 方法。
     * 5 次尝试，退避 1s / 2s / 4s / 8s / 16s。
     */
    @Bean
    public Retry geminiDirectRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))
                .retryOnException(TranslationExecutorConfig::isRetryable)
                .build();
        return RetryRegistry.of(config).retry("geminiDirect");
    }

    /**
     * Phase B 批量轮询网络容错重试。
     * 3 次尝试，退避 5s / 10s / 20s。
     */
    @Bean
    public Retry batchPollRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(5000, 2.0))
                .retryOnException(TranslationExecutorConfig::isRetryable)
                .build();
        return RetryRegistry.of(config).retry("batchPoll");
    }

    public static boolean isRetryable(Throwable e) {
        if (e instanceof DailyQuotaExhaustedException) {
            return false;
        }
        if (e instanceof ServerException se) {
            int code = se.code();
            return code == 500 || code == 502 || code == 503 || code == 504;
        }
        if (e instanceof ApiException ae) {
            if (ae.code() == 429) {
                String msg = ae.getMessage();
                return msg == null || !msg.contains("per_day");
            }
            return false;
        }
        if (e instanceof GenAiIOException) {
            return true;
        }
        return isTimeout(e);
    }

    private static boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof TimeoutException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
