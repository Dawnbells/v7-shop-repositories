package cn.v7soft.admin.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiRateLimitMode;

/**
 * AI 账号级别的并发/限流运行时状态。
 * 支持两种模式：CONCURRENCY（限并发数）和 RPD_RPM（限日请求数 + 分钟请求数）。
 * 由 executeAccountSubTasks 在分发前调用 reserveSlots 预留槽位，Provider 回调后释放。
 */
public class AiAccountRuntimeState {

    private static final int DEFAULT_MAX_CONCURRENCY = 1;

    private final Long aiAccountId;
    private final AtomicInteger inFlightCount = new AtomicInteger(0);
    private LocalDate dayWindowStart = LocalDate.now();
    private LocalDateTime minuteWindowStart = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    private int dayUsed;
    private int minuteUsed;

    public AiAccountRuntimeState(Long aiAccountId) {
        this.aiAccountId = aiAccountId;
    }

    /**
     * 根据账号的流控配置，预留可执行槽位数。
     * <p>
     * CONCURRENCY 模式：available = maxConcurrency - inFlightCount
     * RPD_RPM 模式：available = min(日剩余配额, 分钟剩余配额)
     * <p>
     * 返回实际预留的数量（≤ pendingCount），调用方按此数量分发子任务。
     * 预留后 inFlightCount 增加；子任务完成/失败后由回调调用 releaseFinishedSlot 释放。
     */
    public synchronized int reserveSlots(AiAccount account, int pendingCount) {
        if (pendingCount <= 0) {
            return 0;
        }

        // 确定限流模式，默认为 CONCURRENCY
        AiRateLimitMode mode = account.getRateLimitMode() == null
                ? AiRateLimitMode.CONCURRENCY
                : account.getRateLimitMode();

        // 计算当前可用槽位数
        int available;
        if (mode == AiRateLimitMode.RPD_RPM) {
            // RPD_RPM 模式：刷新日/分钟窗口，取日配额和分钟配额剩余的较小值
            refreshWindows();
            int requestsPerDay = positiveOrZero(account.getRequestsPerDay());
            int requestsPerMinute = positiveOrZero(account.getRequestsPerMinute());
            available = Math.min(requestsPerDay - dayUsed, requestsPerMinute - minuteUsed);
        } else {
            // CONCURRENCY 模式：最大并发数 - 当前飞行中数量
            int maxConcurrency = account.getMaxConcurrency() == null
                    ? DEFAULT_MAX_CONCURRENCY
                    : Math.max(DEFAULT_MAX_CONCURRENCY, account.getMaxConcurrency());
            available = maxConcurrency - inFlightCount.get();
        }

        // 实际预留数 = min(待处理数, 可用数)
        int reserved = Math.min(pendingCount, Math.max(available, 0));
        if (reserved == 0) {
            return 0;
        }

        // 预留槽位：增加飞行中计数
        inFlightCount.addAndGet(reserved);
        if (mode == AiRateLimitMode.RPD_RPM) {
            // RPD_RPM 模式额外消耗日/分钟配额
            dayUsed += reserved;
            minuteUsed += reserved;
        }
        return reserved;
    }

    /** 释放队列中未实际使用的预留槽位（子任务为 null 时） */
    public synchronized void releaseUnusedReservations(int count) {
        for (int i = 0; i < count; i++) {
            releaseFinishedSlot();
        }
    }

    /** 子任务执行完毕（完成/失败/过期）后释放一个槽位 */
    public void releaseFinishedSlot() {
        int current = inFlightCount.decrementAndGet();
        if (current < 0) {
            inFlightCount.compareAndSet(current, 0);
        }
    }

    public int getInFlightCount() {
        return inFlightCount.get();
    }

    private void refreshWindows() {
        LocalDate today = LocalDate.now();
        if (!today.equals(dayWindowStart)) {
            dayWindowStart = today;
            dayUsed = 0;
        }

        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if (!currentMinute.equals(minuteWindowStart)) {
            minuteWindowStart = currentMinute;
            minuteUsed = 0;
        }
    }

    private int positiveOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
