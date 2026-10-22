package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopify access token 管理，按接入方式分两种：
 * <ul>
 *     <li>凭证模式（accessMode 为 null 或 CREDENTIALS）：用店铺自己的 Client ID/Secret 通过 client credentials grant 换取，
 *     token 有效期 24 小时</li>
 *     <li>公开应用（PUBLIC_APP）：OAuth 换得可过期的 access token（1 小时）+ refresh token（90 天，轮换），
 *     用公开应用的 Client ID/Secret 刷新</li>
 * </ul>
 * 两种方式都在临近过期时自动刷新。日志中不打印 token 与 secret。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyTokenService {
    private static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 86399;
    private static final long DEFAULT_PUBLIC_APP_EXPIRES_IN_SECONDS = 3600;
    static final String REAUTHORIZE_MESSAGE = "授权已失效，请重新连接";

    private final RestTemplate restTemplate;
    private final ShopifyWebsiteStore websiteStore;
    private final ShopifyAppProperties appProperties;
    private final ConcurrentHashMap<Long, Object> refreshLocks = new ConcurrentHashMap<>();

    public record ShopifyToken(String accessToken, LocalDateTime expiresAt) {
    }

    /**
     * 公开应用 OAuth 换取或刷新得到的 token
     * @param accessExpiresAt access token 过期时间
     * @param refreshExpiresAt refresh token 过期时间，响应未返回时为 null
     * @param scope 授权的 scope 原文（逗号分隔）
     */
    public record ExchangedToken(String accessToken, LocalDateTime accessExpiresAt, String refreshToken,
                                 LocalDateTime refreshExpiresAt, String scope) {
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
     * 调用 Shopify 换取 token，不落库（凭证模式）
     */
    public ShopifyToken fetchToken(String handle, String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        String url = accessTokenUrl(handle);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(form, headers), String.class);

        JSONObject body = StrUtil.isBlank(response.getBody()) ? new JSONObject() : JSONUtil.parseObj(response.getBody());
        String accessToken = body.getStr("access_token");
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(accessToken, handle);

        Long expiresIn = body.getLong("expires_in");
        long seconds = expiresIn != null && expiresIn > 0 ? expiresIn : DEFAULT_EXPIRES_IN_SECONDS;
        return new ShopifyToken(accessToken, LocalDateTime.now().plusSeconds(seconds));
    }

    /**
     * 公开应用：用 OAuth 回调的 code 换取可过期的 offline token，不落库。
     * HTTP 错误原样抛出（调用方映射为 TOKEN_EXCHANGE_FAILED）。
     */
    public ExchangedToken exchangeAuthorizationCode(String handle, String code) {
        Map<String, Object> body = appCredentialsBody();
        body.put("code", code);
        body.put("expiring", 1);
        return postPublicAppTokenRequest(handle, body, null);
    }

    /**
     * 公开应用：用 refresh token 换取新的 access token（refresh token 同时轮换），不落库。
     * HTTP 错误原样抛出。
     */
    public ExchangedToken refreshPublicAppToken(String handle, String refreshToken) {
        Map<String, Object> body = appCredentialsBody();
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        return postPublicAppTokenRequest(handle, body, refreshToken);
    }

    private String refresh(ThirdPartyWebsiteDto website) {
        if (website.isPublicApp()) {
            return refreshPublicApp(website);
        }
        ShopifyToken token = fetchToken(website.getHandle(), website.getClientId(), website.getClientSecret());
        websiteStore.updateToken(website.getLongId(), token.accessToken(), token.expiresAt());
        log.info("Shopify token refreshed: websiteId={}, handle={}, expiresAt={}",
                website.getId(), website.getHandle(), token.expiresAt());
        return token.accessToken();
    }

    private String refreshPublicApp(ThirdPartyWebsiteDto website) {
        Long websiteId = website.getLongId();
        if (website.getAuthStatus() == ThirdPartyAuthStatusEnum.DISCONNECTED) {
            // 已卸载的店铺保持 DISCONNECTED，不改写为 ERROR
            throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(websiteId, REAUTHORIZE_MESSAGE);
        }
        LocalDateTime refreshExpiresAt = website.getRefreshTokenExpiresAt();
        if (StrUtil.isBlank(website.getRefreshToken())
                || (refreshExpiresAt != null && !refreshExpiresAt.isAfter(LocalDateTime.now()))) {
            log.warn("Shopify public app refresh token missing or expired: websiteId={}, handle={}",
                    websiteId, website.getHandle());
            throw authorizationExpired(websiteId);
        }
        ExchangedToken token;
        try {
            token = refreshPublicAppToken(website.getHandle(), website.getRefreshToken());
        } catch (HttpClientErrorException e) {
            log.warn("Shopify public app token refresh rejected: websiteId={}, handle={}, status={}",
                    websiteId, website.getHandle(), e.getStatusCode().value());
            throw authorizationExpired(websiteId);
        }
        websiteStore.updatePublicAppTokens(websiteId, token);
        log.info("Shopify public app token refreshed: websiteId={}, handle={}, expiresAt={}",
                websiteId, website.getHandle(), token.accessExpiresAt());
        return token.accessToken();
    }

    private BaseException authorizationExpired(Long websiteId) {
        websiteStore.markWebsiteAuthError(websiteId, REAUTHORIZE_MESSAGE);
        return ServiceResponseEnum.ERR_TOKEN_INVALID.newException(websiteId, REAUTHORIZE_MESSAGE);
    }

    private Map<String, Object> appCredentialsBody() {
        if (appProperties == null || !appProperties.isConfigured()) {
            throw ServiceResponseEnum.ERR_SHOPIFY_APP_NOT_CONFIGURED.newException();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", appProperties.getClientId());
        body.put("client_secret", appProperties.getClientSecret());
        return body;
    }

    /**
     * 响应 {access_token, scope, expires_in, refresh_token, refresh_token_expires_in}
     * @param fallbackRefreshToken 响应未返回 refresh_token 时沿用的值
     */
    private ExchangedToken postPublicAppTokenRequest(String handle, Map<String, Object> requestBody, String fallbackRefreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = accessTokenUrl(handle);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);

        JSONObject body = StrUtil.isBlank(response.getBody()) ? new JSONObject() : JSONUtil.parseObj(response.getBody());
        String accessToken = body.getStr("access_token");
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(accessToken, handle);

        LocalDateTime now = LocalDateTime.now();
        Long expiresIn = body.getLong("expires_in");
        long seconds = expiresIn != null && expiresIn > 0 ? expiresIn : DEFAULT_PUBLIC_APP_EXPIRES_IN_SECONDS;
        String refreshToken = StrUtil.blankToDefault(body.getStr("refresh_token"), fallbackRefreshToken);
        Long refreshExpiresIn = body.getLong("refresh_token_expires_in");
        LocalDateTime refreshExpiresAt = refreshExpiresIn != null && refreshExpiresIn > 0 ? now.plusSeconds(refreshExpiresIn) : null;
        return new ExchangedToken(accessToken, now.plusSeconds(seconds), refreshToken, refreshExpiresAt, body.getStr("scope"));
    }

    private String accessTokenUrl(String handle) {
        return "https://" + ShopifyHost.host(handle) + "/admin/oauth/access_token";
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
