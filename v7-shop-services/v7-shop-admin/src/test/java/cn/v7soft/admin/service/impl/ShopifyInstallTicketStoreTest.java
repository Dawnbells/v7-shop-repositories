package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.dto.ShopifyInstallTicket;
import cn.v7soft.dao.enums.CurrencyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyInstallTicketStoreTest {
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private ShopifyInstallTicketStore store;

    @BeforeEach
    void setUp() {
        store = new ShopifyInstallTicketStore(redisTemplate);
    }

    private ShopifyInstallTicket adminTicket() {
        return new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.ADMIN, "test-shop", 7L, 9L,
                "测试店铺", CurrencyMode.PRESENTMENT_MONEY, 11L, "https://admin.example.com", null,
                LocalDateTime.of(2026, 10, 22, 10, 0, 0));
    }

    @Test
    @DisplayName("create：生成 32 字节 hex nonce，按 key 前缀与 TTL 写入 Redis")
    void shouldCreateTicketWithHexNonceAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        String nonce = store.create(adminTicket(), TTL);

        assertTrue(nonce.matches("[0-9a-f]{64}"), "nonce 应为 64 位小写 hex：" + nonce);
        verify(valueOperations).set(keyCaptor.capture(), any(), eq(TTL));
        assertEquals("shopify:install:ticket:" + nonce, keyCaptor.getValue());
    }

    @Test
    @DisplayName("create：每次 nonce 不同，并忽略调用方传入的 nonce")
    void shouldGenerateDistinctNonce() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String preset = "a".repeat(64);

        String first = store.create(adminTicket().withNonce(preset), TTL);
        String second = store.create(adminTicket(), TTL);

        assertNotEquals(preset, first);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("get：读回的票据与写入一致（含 pendingToken 与时间字段）")
    void shouldRoundTripTicket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ShopifyTokenService.ExchangedToken token = new ShopifyTokenService.ExchangedToken("access", LocalDateTime.now().plusHours(1),
                "refresh", LocalDateTime.now().plusDays(90), "read_orders,read_products");
        ShopifyInstallTicket storeTicket = new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.STORE, "test-shop",
                null, null, null, null, null, null, token, null);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

        String nonce = store.create(storeTicket, TTL);
        verify(valueOperations).set(eq("shopify:install:ticket:" + nonce), valueCaptor.capture(), eq(TTL));
        assertInstanceOf(String.class, valueCaptor.getValue(), "值应为 JSON 字符串");
        when(valueOperations.get("shopify:install:ticket:" + nonce)).thenReturn(valueCaptor.getValue());

        ShopifyInstallTicket loaded = store.get(nonce).orElseThrow();

        assertEquals(nonce, loaded.nonce());
        assertEquals(ShopifyInstallTicket.TicketType.STORE, loaded.type());
        assertEquals("test-shop", loaded.handle());
        assertEquals(token, loaded.pendingToken());
        assertNotNull(loaded.createdAt(), "createdAt 为空时应自动填充");
    }

    @Test
    @DisplayName("get：过期（Redis 无值）返回空")
    void shouldReturnEmptyWhenExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String nonce = "b".repeat(64);
        when(valueOperations.get("shopify:install:ticket:" + nonce)).thenReturn(null);

        assertEquals(Optional.empty(), store.get(nonce));
    }

    @Test
    @DisplayName("get：nonce 格式不合法时直接返回空，不访问 Redis")
    void shouldRejectMalformedNonce() {
        assertTrue(store.get(null).isEmpty());
        assertTrue(store.get("abc").isEmpty());
        assertTrue(store.get("../" + "a".repeat(61)).isEmpty());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("delete：删除对应 key，之后 get 为空")
    void shouldDeleteTicket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String nonce = "c".repeat(64);

        store.delete(nonce);

        verify(redisTemplate).delete("shopify:install:ticket:" + nonce);
        when(valueOperations.get("shopify:install:ticket:" + nonce)).thenReturn(null);
        assertTrue(store.get(nonce).isEmpty());
    }

    @Test
    @DisplayName("update：按票据 nonce 覆盖并重置 TTL")
    void shouldUpdateTicketWithNewTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String nonce = "d".repeat(64);
        Duration pendingTtl = Duration.ofMinutes(30);

        store.update(adminTicket().withNonce(nonce), pendingTtl);

        verify(valueOperations).set(eq("shopify:install:ticket:" + nonce), any(String.class), eq(pendingTtl));
    }

    @Test
    @DisplayName("update：nonce 不合法时拒绝写入")
    void shouldRejectUpdateWithoutNonce() {
        assertThrows(IllegalArgumentException.class, () -> store.update(adminTicket(), TTL));
        verifyNoInteractions(redisTemplate);
    }
}
