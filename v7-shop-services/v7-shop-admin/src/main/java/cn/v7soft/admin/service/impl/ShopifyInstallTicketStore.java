package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.dto.ShopifyInstallTicket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Shopify 安装票据存储：Redis key {@code shopify:install:ticket:{nonce}}，值为票据 JSON 字符串。
 * <p>
 * 票据内可能含待绑定 token，值不写日志。过期由 Redis TTL 控制，过期后 {@link #get} 返回空。
 */
@Slf4j
@Service
public class ShopifyInstallTicketStore {
    static final String KEY_PREFIX = "shopify:install:ticket:";
    private static final int NONCE_BYTES = 32;
    private static final Pattern NONCE_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    // 自行序列化为 JSON 字符串，避免 jsonRedisTemplate 的默认类型信息对 record 不友好
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ShopifyInstallTicketStore(@Qualifier("jsonRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存新票据
     * @param ticket 票据，nonce 可为 null（总是重新生成）；createdAt 为 null 时取当前时间
     * @return 新生成的 nonce（32 字节随机数的 hex）
     */
    public String create(ShopifyInstallTicket ticket, Duration ttl) {
        String nonce = newNonce();
        ShopifyInstallTicket toSave = new ShopifyInstallTicket(nonce, ticket.type(), ticket.handle(), ticket.companyId(),
                ticket.userId(), ticket.nickName(), ticket.currencyMode(), ticket.websiteId(), ticket.returnOrigin(),
                ticket.pendingToken(), ticket.createdAt() != null ? ticket.createdAt() : LocalDateTime.now());
        redisTemplate.opsForValue().set(key(nonce), toJson(toSave), ttl);
        return nonce;
    }

    /**
     * 读取票据，不存在、已过期或 nonce 格式不合法时返回空
     */
    public Optional<ShopifyInstallTicket> get(String nonce) {
        if (!isValidNonce(nonce)) {
            return Optional.empty();
        }
        Object value = redisTemplate.opsForValue().get(key(nonce));
        if (!(value instanceof String json) || StrUtil.isBlank(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ShopifyInstallTicket.class));
        } catch (JsonProcessingException e) {
            log.warn("Shopify install ticket unreadable, dropped: nonce prefix={}", nonce.substring(0, 8));
            return Optional.empty();
        }
    }

    /**
     * 覆盖票据并重置 TTL（例如 STORE 票据写入 pendingToken 后）
     */
    public void update(ShopifyInstallTicket ticket, Duration ttl) {
        if (!isValidNonce(ticket.nonce())) {
            throw new IllegalArgumentException("Shopify install ticket nonce is invalid");
        }
        redisTemplate.opsForValue().set(key(ticket.nonce()), toJson(ticket), ttl);
    }

    public void delete(String nonce) {
        if (isValidNonce(nonce)) {
            redisTemplate.delete(key(nonce));
        }
    }

    static String key(String nonce) {
        return KEY_PREFIX + nonce;
    }

    private String newNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isValidNonce(String nonce) {
        return nonce != null && NONCE_PATTERN.matcher(nonce).matches();
    }

    private String toJson(ShopifyInstallTicket ticket) {
        try {
            return objectMapper.writeValueAsString(ticket);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Shopify install ticket serialize failed", e);
        }
    }
}
