package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyTokenServiceTest {
    private static final String TOKEN_URL = "https://test-shop.myshopify.com/admin/oauth/access_token";
    private static final String TOKEN_RESPONSE = "{\"access_token\":\"new-token\",\"scope\":\"read_orders\",\"expires_in\":86399}";

    @Mock private RestTemplate restTemplate;
    @Mock private ShopifyWebsiteStore websiteStore;

    @InjectMocks
    private ShopifyTokenService service;

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
}
