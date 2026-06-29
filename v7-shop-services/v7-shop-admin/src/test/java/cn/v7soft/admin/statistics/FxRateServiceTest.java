package cn.v7soft.admin.statistics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    @SuppressWarnings("rawtypes")
    private HashOperations hashOperations;

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToSeedWhenRedisEmpty() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(FxRateService.REDIS_KEY)).thenReturn(Map.of());

        Map<String, BigDecimal> rates = new FxRateService(redisTemplate).latestUnitsPerUsd();

        assertThat(rates.get("USD")).isEqualByComparingTo(BigDecimal.ONE);
        // 兜底种子里 CZK 是真实口径(~23.3)，而非被扭曲的系统定价系数倒数
        assertThat(rates.get("CZK")).isEqualByComparingTo("23.3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisOverridesSeedAndAddsNewCurrencies() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(hashOperations.entries(anyString()))
                .thenReturn(Map.of("CZK", "23.26", "XAU", "0.0005"));

        Map<String, BigDecimal> rates = new FxRateService(redisTemplate).latestUnitsPerUsd();

        // Redis 刷新值覆盖种子
        assertThat(rates.get("CZK")).isEqualByComparingTo("23.26");
        // Redis 里新增的币种也带出来
        assertThat(rates.get("XAU")).isEqualByComparingTo("0.0005");
        // 种子里其它币种仍在
        assertThat(rates.get("EUR")).isEqualByComparingTo("0.92");
        assertThat(rates.get("USD")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void survivesRedisFailureWithSeed() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString()))
                .thenThrow(new RuntimeException("redis down"));

        Map<String, BigDecimal> rates = new FxRateService(redisTemplate).latestUnitsPerUsd();

        assertThat(rates.get("USD")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rates.get("CZK")).isEqualByComparingTo("23.3");
    }
}
