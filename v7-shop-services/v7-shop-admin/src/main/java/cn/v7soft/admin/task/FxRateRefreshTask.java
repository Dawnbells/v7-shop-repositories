package cn.v7soft.admin.task;

import cn.v7soft.admin.statistics.FxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计真实汇率每日刷新。联网失败仅记日志，沿用 Redis 现有值 / 兜底种子，不影响统计可用性。
 * 首日首次刷新前使用 {@link FxRateService} 的兜底种子（合理近似），刷新后即为最新真实汇率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxRateRefreshTask {

    private final FxRateService fxRateService;

    @Scheduled(cron = "0 30 3 * * ?")
    public void refreshDaily() {
        log.info("[FxRate] 每日真实汇率刷新开始");
        fxRateService.refreshFromApi();
    }
}
