package cn.v7soft.admin.statistics;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 统计专用的「真实汇率」来源。混合方案：兜底种子打底 + Redis 缓存每日联网刷新。
 *
 * <p>口径统一为 <b>units-per-usd（1 美元 = N 该币种）</b>，与个人/临时汇率、换算器内部一致；
 * 因此可直接作为「个人汇率」的默认底座喂入 {@link OrderStatisticsCurrencyConverter}。
 *
 * <p>与店面定价完全解耦：本汇率只用于统计换算，绝不读写 {@code t_currencies.exchange_rate}
 * （那是「基准价 × 它 = 当地售价」的定价系数，语义不同，详见货币换算约定）。
 */
@Slf4j
@Service
public class FxRateService {

    static final String REDIS_KEY = "order-statistics:fx:units-per-usd";
    private static final String FX_API_URL = "https://open.er-api.com/v6/latest/USD";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * 兜底真实汇率（units-per-usd）。Redis 尚未刷新或联网失败时使用，联网成功后被
     * {@link #refreshFromApi()} 覆盖。仅为合理近似，精确值以每日定时拉取结果为准。
     */
    private static final Map<String, String> SEED_UNITS_PER_USD = Map.ofEntries(
            Map.entry("USD", "1"),
            Map.entry("EUR", "0.92"),
            Map.entry("BGN", "1.80"),
            Map.entry("CZK", "23.3"),
            Map.entry("EGP", "49"),
            Map.entry("HUF", "355"),
            Map.entry("IDR", "16300"),
            Map.entry("JPY", "151"),
            Map.entry("MYR", "4.45"),
            Map.entry("PLN", "4.05"),
            Map.entry("RON", "4.6"),
            Map.entry("THB", "33"),
            Map.entry("TWD", "32")
    );

    private final StringRedisTemplate redisTemplate;

    public FxRateService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 最新真实汇率：「1 美元 = N 该币种」（units-per-usd）。先用兜底种子，再用 Redis 中
     * 每日刷新的值覆盖；永远含 USD=1。用于统计「未配置币种默认用真实最新汇率」。
     */
    public Map<String, BigDecimal> latestUnitsPerUsd() {
        Map<String, String> raw = new LinkedHashMap<>(SEED_UNITS_PER_USD);
        try {
            redisTemplate.opsForHash().entries(REDIS_KEY).forEach((code, value) -> {
                if (code != null && value != null) {
                    raw.put(code.toString().toUpperCase(Locale.ROOT), value.toString());
                }
            });
        } catch (RuntimeException exception) {
            log.warn("[FxRate] 读取 Redis 真实汇率失败，使用兜底种子: {}", exception.getMessage());
        }
        LinkedHashMap<String, BigDecimal> rates = new LinkedHashMap<>();
        raw.forEach((code, value) -> {
            try {
                BigDecimal rate = new BigDecimal(value);
                if (rate.compareTo(BigDecimal.ZERO) > 0) {
                    rates.put(code, rate);
                }
            } catch (NumberFormatException ignored) {
                // 跳过脏值，由其它币种或「缺失汇率」逻辑兜底
            }
        });
        rates.put("USD", BigDecimal.ONE);
        return rates;
    }

    /**
     * 从外部免密钥汇率 API 拉取最新真实汇率写入 Redis；失败时保留现有值（兜底种子仍生效）。
     * open.er-api.com 以 USD 为基准返回，{@code rates} 即「1 美元 = N 该币种」，与本系统口径一致。
     */
    public void refreshFromApi() {
        try {
            String response = HttpUtil.get(FX_API_URL, TIMEOUT_MS);
            JSONObject body = JSONUtil.parseObj(response);
            if (!"success".equalsIgnoreCase(body.getStr("result", ""))) {
                log.warn("[FxRate] 汇率 API 返回非 success，跳过本次刷新: {}", body.getStr("result"));
                return;
            }
            JSONObject rates = body.getJSONObject("rates");
            if (rates == null || rates.isEmpty()) {
                log.warn("[FxRate] 汇率 API 未返回 rates，跳过本次刷新");
                return;
            }
            Map<String, String> toStore = new LinkedHashMap<>();
            for (String code : rates.keySet()) {
                BigDecimal rate = rates.getBigDecimal(code);
                if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                    toStore.put(code.toUpperCase(Locale.ROOT),
                            rate.stripTrailingZeros().toPlainString());
                }
            }
            toStore.put("USD", "1");
            redisTemplate.opsForHash().putAll(REDIS_KEY, toStore);
            log.info("[FxRate] 已刷新 {} 个币种真实汇率（units-per-usd）", toStore.size());
        } catch (RuntimeException exception) {
            log.warn("[FxRate] 拉取真实汇率失败，沿用现有值: {}", exception.getMessage());
        }
    }
}
