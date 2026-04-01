package cn.v7soft.admin.service.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * AI 翻译任务的可观测性指标（纯 JDK 实现，不依赖 Micrometer）。
 * 每 5 分钟通过日志输出累计指标快照；调用方在关键节点调用 record* 方法。
 */
@Slf4j
@Component
public class TranslateTaskMetrics {

    private final AtomicLong submitTotal = new AtomicLong();
    private final AtomicLong completedTotal = new AtomicLong();
    private final AtomicLong failedTotal = new AtomicLong();
    private final AtomicLong timeoutTotal = new AtomicLong();
    private final AtomicLong fallbackTextTotal = new AtomicLong();
    private final AtomicLong fallbackHtmlTotal = new AtomicLong();
    private final AtomicLong fallbackImageTotal = new AtomicLong();
    private final AtomicLong dedupHitTotal = new AtomicLong();
    private final AtomicLong durationSumMs = new AtomicLong();
    private final AtomicLong durationCount = new AtomicLong();

    public void recordSubmit()        { submitTotal.incrementAndGet(); }
    public void recordCompleted()     { completedTotal.incrementAndGet(); }
    public void recordFailed()        { failedTotal.incrementAndGet(); }
    public void recordTimeout()       { timeoutTotal.incrementAndGet(); }
    public void recordFallbackText()  { fallbackTextTotal.incrementAndGet(); }
    public void recordFallbackHtml()  { fallbackHtmlTotal.incrementAndGet(); }
    public void recordFallbackImage() { fallbackImageTotal.incrementAndGet(); }
    public void recordDedupHit()      { dedupHitTotal.incrementAndGet(); }

    public void recordDuration(long durationMs) {
        durationSumMs.addAndGet(durationMs);
        durationCount.incrementAndGet();
    }

    public long getSubmitTotal()        { return submitTotal.get(); }
    public long getCompletedTotal()     { return completedTotal.get(); }
    public long getFailedTotal()        { return failedTotal.get(); }
    public long getTimeoutTotal()       { return timeoutTotal.get(); }
    public long getFallbackTextTotal()  { return fallbackTextTotal.get(); }
    public long getFallbackHtmlTotal()  { return fallbackHtmlTotal.get(); }
    public long getFallbackImageTotal() { return fallbackImageTotal.get(); }
    public long getDedupHitTotal()      { return dedupHitTotal.get(); }

    public long getAvgDurationMs() {
        long count = durationCount.get();
        return count > 0 ? durationSumMs.get() / count : 0;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void logMetricsSnapshot() {
        long completed = completedTotal.get();
        long failed = failedTotal.get();
        long total = completed + failed;
        double failRate = total > 0 ? (double) failed / total * 100 : 0;

        log.info("[TranslateMetrics] submit={}, completed={}, failed={}, timeout={}, " +
                        "dedupHit={}, fallback(text/html/img)={}/{}/{}, avgDuration={}ms, failRate={}%",
                submitTotal.get(), completed, failed, timeoutTotal.get(),
                dedupHitTotal.get(),
                fallbackTextTotal.get(), fallbackHtmlTotal.get(), fallbackImageTotal.get(),
                getAvgDurationMs(), String.format("%.1f", failRate));

        if (failRate > 20 && total >= 5) {
            log.warn("[TranslateMetrics] 翻译任务失败率 {}% 超过 20% 阈值 (completed={}, failed={})",
                    String.format("%.1f", failRate), completed, failed);
        }
        if (timeoutTotal.get() > 3) {
            log.warn("[TranslateMetrics] 翻译任务超时数 {} 超过告警阈值", timeoutTotal.get());
        }
    }
}
