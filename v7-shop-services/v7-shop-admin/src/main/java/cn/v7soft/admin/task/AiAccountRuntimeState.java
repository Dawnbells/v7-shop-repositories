package cn.v7soft.admin.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiRateLimitMode;

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

    public synchronized int reserveSlots(AiAccount account, int pendingCount) {
        if (pendingCount <= 0) {
            return 0;
        }

        AiRateLimitMode mode = account.getRateLimitMode() == null
                ? AiRateLimitMode.CONCURRENCY
                : account.getRateLimitMode();
        int available;
        if (mode == AiRateLimitMode.RPD_RPM) {
            refreshWindows();
            int requestsPerDay = positiveOrZero(account.getRequestsPerDay());
            int requestsPerMinute = positiveOrZero(account.getRequestsPerMinute());
            available = Math.min(requestsPerDay - dayUsed, requestsPerMinute - minuteUsed);
        } else {
            int maxConcurrency = account.getMaxConcurrency() == null
                    ? DEFAULT_MAX_CONCURRENCY
                    : Math.max(DEFAULT_MAX_CONCURRENCY, account.getMaxConcurrency());
            available = maxConcurrency - inFlightCount.get();
        }

        int reserved = Math.min(pendingCount, Math.max(available, 0));
        if (reserved <= 0) {
            return 0;
        }

        inFlightCount.addAndGet(reserved);
        if (mode == AiRateLimitMode.RPD_RPM) {
            dayUsed += reserved;
            minuteUsed += reserved;
        }
        return reserved;
    }

    public synchronized void releaseUnusedReservations(int count) {
        for (int i = 0; i < count; i++) {
            releaseFinishedSlot();
        }
    }

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
