package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyTokenServiceTest {
    private static final String TOKEN_URL = "https://test-shop.myshopify.com/admin/oauth/access_token";
    private static final String TOKEN_RESPONSE = "{\"access_token\":\"new-token\",\"scope\":\"read_orders\",\"expires_in\":86399}";

    private static final String EXCHANGE_RESPONSE = "{\"access_token\":\"app-access\",\"scope\":\"read_orders,read_products\","
            + "\"expires_in\":3600,\"refresh_token\":\"app-refresh\",\"refresh_token_expires_in\":7776000}";
    private static final String REFRESH_RESPONSE = "{\"access_token\":\"app-access-2\",\"scope\":\"read_orders,read_all_orders\","
            + "\"expires_in\":3600,\"refresh_token\":\"app-refresh-2\",\"refresh_token_expires_in\":7776000}";

    @Mock private RestTemplate restTemplate;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Spy private ShopifyAppProperties appProperties = appProperties("app-client-id", "app-client-secret");

    @InjectMocks
    private ShopifyTokenService service;

    private static ShopifyAppProperties appProperties(String clientId, String clientSecret) {
        ShopifyAppProperties properties = new ShopifyAppProperties();
        properties.setClientId(clientId);
        properties.setClientSecret(clientSecret);
        return properties;
    }

    private ThirdPartyWebsiteDto buildPublicAppDto(String token, LocalDateTime expiresAt, String refreshToken) {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .handle("test-shop")
                .token(token)
                .tokenExpiresAt(expiresAt)
                .refreshToken(refreshToken)
                .refreshTokenExpiresAt(LocalDateTime.now().plusDays(80))
                .accessMode(ShopifyAccessMode.PUBLIC_APP)
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .build();
    }

    private static HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private ThirdPartyWebsiteDto buildWebsiteDto(String token, LocalDateTime expiresAt) {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .handle("test-shop")
                .clientId("client-id")
                .clientSecret("client-secret")
                .token(token)
                .tokenExpiresAt(expiresAt)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .build();
    }

    @Test
    void shouldRejectUnsafeStoredHandleBeforeSendingCredentials() {
        assertThrows(BaseException.class, () -> service.fetchToken("audit.example?", "client-id", "stored-secret"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("fetchToken 应以表单方式提交 client credentials 并解析过期时间")
    void shouldFetchTokenWithClientCredentials() {
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(TOKEN_URL), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TOKEN_RESPONSE, HttpStatus.OK));

        ShopifyTokenService.ShopifyToken token = service.fetchToken("test-shop", "client-id", "client-secret");

        assertEquals("new-token", token.accessToken());
        assertTrue(token.expiresAt().isAfter(LocalDateTime.now().plusHours(23)));
        HttpEntity<MultiValueMap<String, String>> entity = captor.getValue();
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, entity.getHeaders().getContentType());
        assertEquals("client_credentials", entity.getBody().getFirst("grant_type"));
        assertEquals("client-id", entity.getBody().getFirst("client_id"));
        assertEquals("client-secret", entity.getBody().getFirst("client_secret"));
    }

    @Test
    @DisplayName("响应中没有 access_token 时应抛出异常")
    void shouldThrowWhenNoAccessToken() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        assertThrows(BaseException.class, () -> service.fetchToken("test-shop", "client-id", "client-secret"));
    }

    @Test
    @DisplayName("token 未临近过期时直接使用，不请求 Shopify")
    void shouldReuseTokenWhenNotExpiring() {
        String token = service.getAccessToken(buildWebsiteDto("old-token", LocalDateTime.now().plusHours(10)));

        assertEquals("old-token", token);
        verifyNoInteractions(restTemplate, websiteStore);
    }

    @Test
    @DisplayName("token 临近过期时应刷新并落库")
    void shouldRefreshWhenExpiring() {
        ThirdPartyWebsiteDto expiring = buildWebsiteDto("old-token", LocalDateTime.now().plusMinutes(2));
        when(websiteStore.getDtoById(1L)).thenReturn(expiring);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TOKEN_RESPONSE, HttpStatus.OK));

        String token = service.getAccessToken(expiring);

        assertEquals("new-token", token);
        verify(websiteStore).updateToken(eq(1L), eq("new-token"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("token 临近过期但已被其它线程刷新时直接使用库里的 token")
    void shouldUseTokenRefreshedByOthers() {
        when(websiteStore.getDtoById(1L)).thenReturn(buildWebsiteDto("refreshed-token", LocalDateTime.now().plusHours(23)));

        String token = service.getAccessToken(buildWebsiteDto("old-token", LocalDateTime.now().minusMinutes(1)));

        assertEquals("refreshed-token", token);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("forceRefresh：库里仍是被拒绝的 token 时应重新换取")
    void shouldRefetchWhenRejectedTokenStillStored() {
        when(websiteStore.getDtoById(1L)).thenReturn(buildWebsiteDto("old-token", LocalDateTime.now().plusHours(10)));
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TOKEN_RESPONSE, HttpStatus.OK));

        String token = service.forceRefresh(1L, "old-token");

        assertEquals("new-token", token);
        verify(websiteStore).updateToken(eq(1L), eq("new-token"), any(LocalDateTime.class));
    }

    // ==================== 公开应用 ====================

    @Test
    @DisplayName("accessMode=CREDENTIALS 时仍走 client credentials，不使用公开应用凭证")
    void shouldUseClientCredentialsForCredentialsMode() {
        ThirdPartyWebsiteDto expiring = buildWebsiteDto("old-token", LocalDateTime.now().plusMinutes(2));
        expiring.setAccessMode(ShopifyAccessMode.CREDENTIALS);
        when(websiteStore.getDtoById(1L)).thenReturn(expiring);
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(TOKEN_URL), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TOKEN_RESPONSE, HttpStatus.OK));

        assertEquals("new-token", service.getAccessToken(expiring));

        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, captor.getValue().getHeaders().getContentType());
        verify(websiteStore).updateToken(eq(1L), eq("new-token"), any(LocalDateTime.class));
        verify(websiteStore, never()).updatePublicAppTokens(anyLong(), any());
    }

    @Test
    @DisplayName("exchangeAuthorizationCode：JSON 提交 expiring=1，解析 refresh_token 与过期时间")
    @SuppressWarnings("unchecked")
    void shouldExchangeAuthorizationCodeForExpiringToken() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(TOKEN_URL), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(EXCHANGE_RESPONSE, HttpStatus.OK));

        ShopifyTokenService.ExchangedToken token = service.exchangeAuthorizationCode("test-shop", "auth-code");

        assertEquals("app-access", token.accessToken());
        assertEquals("app-refresh", token.refreshToken());
        assertEquals("read_orders,read_products", token.scope());
        assertTrue(token.accessExpiresAt().isAfter(LocalDateTime.now().plusMinutes(59)));
        assertTrue(token.accessExpiresAt().isBefore(LocalDateTime.now().plusMinutes(61)));
        assertTrue(token.refreshExpiresAt().isAfter(LocalDateTime.now().plusDays(89)));

        HttpEntity<Map<String, Object>> entity = captor.getValue();
        assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());
        Map<String, Object> body = entity.getBody();
        assertEquals("app-client-id", body.get("client_id"));
        assertEquals("app-client-secret", body.get("client_secret"));
        assertEquals("auth-code", body.get("code"));
        assertEquals(1, body.get("expiring"));
    }

    @Test
    @DisplayName("refreshPublicAppToken：JSON 提交 grant_type=refresh_token 与 refresh_token")
    @SuppressWarnings("unchecked")
    void shouldRefreshPublicAppTokenWithRefreshGrant() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(TOKEN_URL), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(REFRESH_RESPONSE, HttpStatus.OK));

        ShopifyTokenService.ExchangedToken token = service.refreshPublicAppToken("test-shop", "app-refresh");

        assertEquals("app-access-2", token.accessToken());
        assertEquals("app-refresh-2", token.refreshToken());
        Map<String, Object> body = captor.getValue().getBody();
        assertEquals("refresh_token", body.get("grant_type"));
        assertEquals("app-refresh", body.get("refresh_token"));
        assertEquals("app-client-id", body.get("client_id"));
        assertEquals("app-client-secret", body.get("client_secret"));
        assertFalse(body.containsKey("expiring"));
    }

    @Test
    @DisplayName("refresh 响应未返回 refresh_token 时沿用原值")
    void shouldKeepRefreshTokenWhenResponseOmitsIt() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"access_token\":\"a\",\"expires_in\":3600}", HttpStatus.OK));

        ShopifyTokenService.ExchangedToken token = service.refreshPublicAppToken("test-shop", "keep-me");

        assertEquals("keep-me", token.refreshToken());
        assertNull(token.refreshExpiresAt());
    }

    @Test
    @DisplayName("公开应用未配置 client_id/secret 时抛 ERR_SHOPIFY_APP_NOT_CONFIGURED，不发请求")
    void shouldFailWhenPublicAppNotConfigured() {
        appProperties.setClientSecret("");

        BaseException ex = assertThrows(BaseException.class, () -> service.exchangeAuthorizationCode("test-shop", "code"));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_APP_NOT_CONFIGURED, ex.getResponseEnum());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUBLIC_APP：token 临期时用 refresh_token 刷新并保存双 token")
    void shouldRefreshPublicAppTokenWhenExpiring() {
        ThirdPartyWebsiteDto expiring = buildPublicAppDto("app-access", LocalDateTime.now().plusMinutes(2), "app-refresh");
        when(websiteStore.getDtoById(1L)).thenReturn(expiring);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(REFRESH_RESPONSE, HttpStatus.OK));
        ArgumentCaptor<ShopifyTokenService.ExchangedToken> captor = ArgumentCaptor.forClass(ShopifyTokenService.ExchangedToken.class);

        String token = service.getAccessToken(expiring);

        assertEquals("app-access-2", token);
        verify(websiteStore).updatePublicAppTokens(eq(1L), captor.capture());
        assertEquals("app-refresh-2", captor.getValue().refreshToken());
        verify(websiteStore, never()).updateToken(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("PUBLIC_APP：forceRefresh 走 refresh_token 刷新")
    void shouldForceRefreshPublicAppToken() {
        when(websiteStore.getDtoById(1L)).thenReturn(buildPublicAppDto("app-access", LocalDateTime.now().plusMinutes(50), "app-refresh"));
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(REFRESH_RESPONSE, HttpStatus.OK));

        assertEquals("app-access-2", service.forceRefresh(1L, "app-access"));
        verify(websiteStore).updatePublicAppTokens(eq(1L), any());
    }

    @Test
    @DisplayName("PUBLIC_APP：refresh 返回 4xx 时标记「授权已失效，请重新连接」并抛 ERR_TOKEN_INVALID")
    void shouldMarkReauthorizeWhenRefreshRejected() {
        ThirdPartyWebsiteDto expiring = buildPublicAppDto("app-access", LocalDateTime.now().minusMinutes(1), "app-refresh");
        when(websiteStore.getDtoById(1L)).thenReturn(expiring);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(String.class))).thenThrow(httpError(HttpStatus.BAD_REQUEST));

        BaseException ex = assertThrows(BaseException.class, () -> service.getAccessToken(expiring));

        assertEquals(ServiceResponseEnum.ERR_TOKEN_INVALID, ex.getResponseEnum());
        verify(websiteStore).markWebsiteAuthError(1L, "授权已失效，请重新连接");
        verify(websiteStore, never()).updatePublicAppTokens(anyLong(), any());
    }

    @Test
    @DisplayName("PUBLIC_APP：没有 refresh_token 时同样标记重新连接，不发请求")
    void shouldMarkReauthorizeWhenRefreshTokenMissing() {
        ThirdPartyWebsiteDto expiring = buildPublicAppDto("app-access", LocalDateTime.now().minusMinutes(1), null);
        when(websiteStore.getDtoById(1L)).thenReturn(expiring);

        BaseException ex = assertThrows(BaseException.class, () -> service.getAccessToken(expiring));

        assertEquals(ServiceResponseEnum.ERR_TOKEN_INVALID, ex.getResponseEnum());
        verify(websiteStore).markWebsiteAuthError(1L, "授权已失效，请重新连接");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("PUBLIC_APP：已断开的店铺不刷新、不改写为 ERROR")
    void shouldNotTouchDisconnectedWebsite() {
        ThirdPartyWebsiteDto disconnected = buildPublicAppDto("DISCONNECTED:1", LocalDateTime.now().minusMinutes(1), null);
        disconnected.setAuthStatus(ThirdPartyAuthStatusEnum.DISCONNECTED);
        when(websiteStore.getDtoById(1L)).thenReturn(disconnected);

        assertThrows(BaseException.class, () -> service.getAccessToken(disconnected));

        verify(websiteStore, never()).markWebsiteAuthError(anyLong(), anyString());
        verifyNoInteractions(restTemplate);
    }
}
