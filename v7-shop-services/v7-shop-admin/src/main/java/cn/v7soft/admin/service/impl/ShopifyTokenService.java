package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ServiceResponseEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopify access token 管理：用 Client ID/Secret 通过 client credentials grant 换取，
 * token 有效期 24 小时，临近过期时自动刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyTokenService {
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 86399;

    private final RestTemplate restTemplate;
    private final ShopifyWebsiteStore websiteStore;
    private final ConcurrentHashMap<Long, Object> refreshLocks = new ConcurrentHashMap<>();

    public record ShopifyToken(String accessToken, LocalDateTime expiresAt) {
    }

    /**
     * 返回可用的 token，临近过期时先刷新
     */
    public String getAccessToken(ThirdPartyWebsiteDto website) {
        if (isUsable(website.getToken(), website.getTokenExpiresAt())) {
            return website.getToken();
        }
        synchronized (lockOf(website.getLongId())) {
            // 可能已被其它线程刷新，重新读取
            ThirdPartyWebsiteDto latest = websiteStore.getDtoById(website.getLongId());
            if (isUsable(latest.getToken(), latest.getTokenExpiresAt())) {
                return latest.getToken();
            }
            return refresh(latest);
        }
    }

    /**
     * token 被 Shopify 拒绝（401）时强制刷新
     * @param rejectedToken 被拒绝的 token，若库里已是另一个可用 token 则直接返回库里的
     */
    public String forceRefresh(Long websiteId, String rejectedToken) {
        synchronized (lockOf(websiteId)) {
            ThirdPartyWebsiteDto latest = websiteStore.getDtoById(websiteId);
            if (!StrUtil.equals(latest.getToken(), rejectedToken)
                    && isUsable(latest.getToken(), latest.getTokenExpiresAt())) {
                return latest.getToken();
            }
            return refresh(latest);
        }
    }

    /**
     * 调用 Shopify 换取 token，不落库
     */
    public ShopifyToken fetchToken(String handle, String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        String url = "https://" + handle + ".myshopify.com/admin/oauth/access_token";
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);

        JSONObject body = StrUtil.isBlank(response.getBody()) ? new JSONObject() : JSONUtil.parseObj(response.getBody());
        String accessToken = body.getStr("access_token");
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(accessToken, handle);

        Long expiresIn = body.getLong("expires_in");
        long seconds = expiresIn != null && expiresIn > 0 ? expiresIn : DEFAULT_EXPIRES_IN_SECONDS;
        return new ShopifyToken(accessToken, LocalDateTime.now().plusSeconds(seconds));
    }

    private String refresh(ThirdPartyWebsiteDto website) {
        ShopifyToken token = fetchToken(website.getHandle(), website.getClientId(), website.getClientSecret());
        websiteStore.updateToken(website.getLongId(), token.accessToken(), token.expiresAt());
        log.info("Shopify token refreshed: websiteId={}, handle={}, expiresAt={}",
                website.getId(), website.getHandle(), token.expiresAt());
        return token.accessToken();
    }

    private boolean isUsable(String token, LocalDateTime expiresAt) {
        return StrUtil.isNotBlank(token)
                && expiresAt != null
                && expiresAt.minus(REFRESH_BEFORE_EXPIRY).isAfter(LocalDateTime.now());
    }

    private Object lockOf(Long websiteId) {
        return refreshLocks.computeIfAbsent(websiteId, id -> new Object());
    }
}
