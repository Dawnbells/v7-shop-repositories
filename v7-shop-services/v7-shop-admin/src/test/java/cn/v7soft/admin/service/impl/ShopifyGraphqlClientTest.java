package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONObject;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyGraphqlClientTest {
    private static final String URL = "https://test-shop.myshopify.com/admin/api/2026-07/graphql.json";
    private static final String QUERY = "query($q: String) { ordersCount(query: $q) { count } }";
    private static final String OK_BODY = "{\"data\":{\"ordersCount\":{\"count\":5}},"
            + "\"extensions\":{\"cost\":{\"requestedQueryCost\":1,\"actualQueryCost\":1,"
            + "\"throttleStatus\":{\"maximumAvailable\":2000.0,\"currentlyAvailable\":1999,\"restoreRate\":100.0}}}}";
    private static final String THROTTLED_BODY = "{\"errors\":[{\"message\":\"Throttled\",\"extensions\":{\"code\":\"THROTTLED\"}}],"
            + "\"extensions\":{\"cost\":{\"requestedQueryCost\":1000,\"actualQueryCost\":null,"
            + "\"throttleStatus\":{\"maximumAvailable\":2000.0,\"currentlyAvailable\":500,\"restoreRate\":100.0}}}}";
    private static final String USER_ERROR_BODY = "{\"errors\":[{\"message\":\"Field 'foo' doesn't exist on type 'QueryRoot'\","
            + "\"extensions\":{\"code\":\"undefinedField\"}}]}";

    @Mock private RestTemplate restTemplate;
    @Mock private ShopifyTokenService tokenService;
    @Mock private ShopifyWebsiteStore websiteStore;

    private final List<Long> sleeps = new ArrayList<>();
    private ShopifyGraphqlClient client;

    @BeforeEach
    void setUp() {
        ShopifyAppProperties properties = new ShopifyAppProperties();
        properties.setApiVersion("2026-07");
        client = new ShopifyGraphqlClient(restTemplate, tokenService, websiteStore, properties) {
            @Override
            protected void sleep(long millis) {
                sleeps.add(millis);
            }
        };
    }

    private ThirdPartyWebsiteDto website(String handle) {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .handle(handle)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .build();
    }

    private static ResponseEntity<String> ok(String body) {
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    private static HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("成功：POST graphql.json，携带 token 头、query 与 variables，返回 data")
    @SuppressWarnings("unchecked")
    void shouldPostQueryAndReturnData() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(URL), captor.capture(), eq(String.class))).thenReturn(ok(OK_BODY));

        ShopifyGraphqlClient.GraphqlResult result = client.execute(website("test-shop"), QUERY, Map.of("q", "status:any"));

        assertEquals(5, result.data().getJSONObject("ordersCount").getInt("count"));
        assertNotNull(result.extensions());
        HttpEntity<Map<String, Object>> entity = captor.getValue();
        assertEquals("token-1", entity.getHeaders().getFirst("X-Shopify-Access-Token"));
        assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());
        assertEquals(QUERY, entity.getBody().get("query"));
        assertEquals(Map.of("q", "status:any"), entity.getBody().get("variables"));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("401：强制刷新 token 后用新 token 重试一次")
    @SuppressWarnings("unchecked")
    void shouldRefreshTokenOnceOn401() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(tokenService.forceRefresh(1L, "token-1")).thenReturn("token-2");
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(eq(URL), captor.capture(), eq(String.class)))
                .thenThrow(httpError(HttpStatus.UNAUTHORIZED))
                .thenReturn(ok(OK_BODY));

        ShopifyGraphqlClient.GraphqlResult result = client.execute(website("test-shop"), QUERY, null);

        assertNotNull(result.data());
        verify(tokenService).forceRefresh(1L, "token-1");
        assertEquals("token-2", captor.getAllValues().get(1).getHeaders().getFirst("X-Shopify-Access-Token"));
    }

    @Test
    @DisplayName("401：刷新后仍 401 时不再重试，抛出原异常")
    void shouldNotRetryTwiceOn401() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(tokenService.forceRefresh(1L, "token-1")).thenReturn("token-2");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class)))
                .thenThrow(httpError(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> client.execute(website("test-shop"), QUERY, null));
        verify(tokenService, times(1)).forceRefresh(anyLong(), anyString());
        verify(restTemplate, times(2)).postForEntity(eq(URL), any(), eq(String.class));
    }

    @Test
    @DisplayName("THROTTLED：按 throttleStatus 等待后重试，成功返回")
    void shouldBackOffAndRetryWhenThrottled() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class)))
                .thenReturn(ok(THROTTLED_BODY), ok(THROTTLED_BODY), ok(OK_BODY));

        ShopifyGraphqlClient.GraphqlResult result = client.execute(website("test-shop"), QUERY, null);

        assertNotNull(result.data());
        // (1000 - 500) / 100 = 5 秒
        assertEquals(List.of(5000L, 5000L), sleeps);
        verify(restTemplate, times(3)).postForEntity(eq(URL), any(), eq(String.class));
    }

    @Test
    @DisplayName("THROTTLED：最多重试 3 次，仍被限流则提示 GraphQL 请求失败")
    void shouldGiveUpAfterThreeThrottleRetries() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ok(THROTTLED_BODY));

        BaseException ex = assertThrows(BaseException.class, () -> client.execute(website("test-shop"), QUERY, null));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL, ex.getResponseEnum());
        assertEquals(3, sleeps.size());
        verify(restTemplate, times(4)).postForEntity(eq(URL), any(), eq(String.class));
    }

    @Test
    @DisplayName("其他 errors：不重试，保留 GraphQL 错误详情，不误报 token 失效")
    void shouldThrowOnUserErrors() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ok(USER_ERROR_BODY));

        BaseException ex = assertThrows(BaseException.class, () -> client.execute(website("test-shop"), QUERY, null));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL, ex.getResponseEnum());
        assertTrue(ex.getMessage().contains("Field 'foo'"));
        verify(restTemplate, times(1)).postForEntity(eq(URL), any(), eq(String.class));
        assertTrue(sleeps.isEmpty());
        verifyNoInteractions(websiteStore);
    }

    @Test
    @DisplayName("缺少 read_customers：拒绝部分订单数据，保留权限原因，不刷新 token 或停用店铺")
    void shouldReportMissingCustomerScope() {
        String body = """
                {"data":{"orders":{"nodes":[{"id":"gid://shopify/Order/1","customer":null}]}},
                 "errors":[{"message":"Access denied for customer field. Required access: `read_customers` access scope.",
                 "extensions":{"code":"ACCESS_DENIED","requiredAccess":"`read_customers` access scope."},
                 "path":["orders","nodes",0,"customer"]}]}
                """;
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ok(body));

        ThirdPartyWebsiteDto website = website("test-shop");
        website.setId("2442700183043");
        BaseException ex = assertThrows(BaseException.class, () -> client.execute(website, QUERY));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_ACCESS_DENIED, ex.getResponseEnum());
        assertTrue(ex.getMessage().contains("read_customers"));
        assertTrue(ex.getMessage().contains("2442700183043"));
        assertFalse(ex.getMessage().contains("Token已失效"));
        verify(restTemplate, times(1)).postForEntity(eq(URL), any(), eq(String.class));
        verify(tokenService, never()).forceRefresh(anyLong(), anyString());
        verifyNoInteractions(websiteStore);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("受保护数据审批不足：即使同时限流也优先提示权限问题，不自动重试")
    void shouldReportDataApprovalDenialBeforeThrottling() {
        String body = """
                {"errors":[
                  {"message":"Throttled","extensions":{"code":"THROTTLED"}},
                  {"message":"This app is not approved to access the Customer object.",
                   "extensions":{"code":"ACCESS_DENIED"}}
                ]}
                """;
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ok(body));

        BaseException ex = assertThrows(BaseException.class, () -> client.execute(website("test-shop"), QUERY));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_ACCESS_DENIED, ex.getResponseEnum());
        assertTrue(ex.getMessage().contains("not approved"));
        verify(restTemplate, times(1)).postForEntity(eq(URL), any(), eq(String.class));
        verify(tokenService, never()).forceRefresh(anyLong(), anyString());
        verifyNoInteractions(websiteStore);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("空响应：报告接口异常，不误报 token 失效")
    void shouldReportEmptyResponse() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ok(""));

        BaseException ex = assertThrows(BaseException.class, () -> client.execute(website("test-shop"), QUERY));

        assertEquals(ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL, ex.getResponseEnum());
        assertTrue(ex.getMessage().contains("HTTP 200"));
        verify(tokenService, never()).forceRefresh(anyLong(), anyString());
        verifyNoInteractions(websiteStore);
    }

    @Test
    @DisplayName("HTTP 402：标记店铺不可用并抛出原异常")
    void shouldMarkAuthErrorOn402() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenThrow(httpError(HttpStatus.PAYMENT_REQUIRED));

        assertThrows(HttpClientErrorException.class, () -> client.execute(website("test-shop"), QUERY, null));

        verify(websiteStore).markWebsiteAuthError(eq(1L), contains("HTTP 402"));
    }

    @Test
    @DisplayName("HTTP 423：标记店铺不可用并抛出原异常")
    void shouldMarkAuthErrorOn423() {
        when(tokenService.getAccessToken(any())).thenReturn("token-1");
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenThrow(httpError(HttpStatus.LOCKED));

        assertThrows(HttpClientErrorException.class, () -> client.execute(website("test-shop"), QUERY, null));

        verify(websiteStore).markWebsiteAuthError(eq(1L), contains("HTTP 423"));
    }

    @Test
    @DisplayName("库里的 handle 不合法时不发请求、不取 token")
    void shouldRejectUnsafeHandle() {
        assertThrows(BaseException.class, () -> client.execute(website("evil.example?"), QUERY, null));
        verifyNoInteractions(restTemplate, tokenService);
    }

    @Test
    @DisplayName("throttleWaitMillis：缺少 throttleStatus 时默认 1 秒，并限制在 0.5～10 秒")
    void shouldComputeThrottleWait() {
        assertEquals(1000L, ShopifyGraphqlClient.throttleWaitMillis(null));
        assertEquals(1000L, ShopifyGraphqlClient.throttleWaitMillis(new JSONObject()));

        JSONObject plenty = new JSONObject("{\"cost\":{\"requestedQueryCost\":10,"
                + "\"throttleStatus\":{\"currentlyAvailable\":1000,\"restoreRate\":100}}}");
        assertEquals(500L, ShopifyGraphqlClient.throttleWaitMillis(plenty));

        JSONObject empty = new JSONObject("{\"cost\":{\"requestedQueryCost\":2000,"
                + "\"throttleStatus\":{\"currentlyAvailable\":0,\"restoreRate\":50}}}");
        assertEquals(10_000L, ShopifyGraphqlClient.throttleWaitMillis(empty));
    }
}
