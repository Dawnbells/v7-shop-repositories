package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ShopifyCancelCheckResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyOrderSyncServiceTest {

    private static final String GRAPHQL_URL = "https://test-shop.myshopify.com/admin/api/2026-07/graphql.json";

    @Mock private RestTemplate restTemplate;
    @Mock private ShopifyTokenService tokenService;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Mock private ICurrencyService currencyService;
    @Mock private ILanguageService languageService;
    @Mock private ICountryService countryService;
    @Mock private IAddressService addressService;
    @Mock private ITemporaryOrderService temporaryOrderService;
    @Mock private IProductSKUService productSKUService;
    @Mock private SystemUserRepository systemUserRepository;

    private ShopifyOrderSyncService service;

    @BeforeEach
    void setUp() {
        ShopifyAppProperties appProperties = new ShopifyAppProperties();
        // 真实客户端 + mock RestTemplate：401 刷新、402/423 停用、限流退避都走客户端逻辑
        ShopifyGraphqlClient graphqlClient = new ShopifyGraphqlClient(restTemplate, tokenService, websiteStore, appProperties) {
            @Override
            protected void sleep(long millis) {
            }
        };
        service = new ShopifyOrderSyncService(graphqlClient, restTemplate, appProperties, tokenService, websiteStore,
                currencyService, languageService, countryService, addressService, temporaryOrderService,
                productSKUService, systemUserRepository);
    }

    @Test
    void shouldRejectUnsafeStoredHandleBeforeSendingToken() {
        ThirdPartyWebsiteDto website = buildWebsiteDto(null);
        website.setHandle("audit.example?");
        assertThrows(BaseException.class, () -> service.loadOrders(website, buildRequest(null), "", SyncMode.AUTO));
        verifyNoInteractions(restTemplate, tokenService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2})
    void shouldResolveMetafieldOwnerWithinWebsiteCompany(int matchType) {
        boolean telephoneMatches = matchType == 1;
        JSONObject node = bareNode("5500000000001", "2026-06-01T10:30:00-04:00");
        node.set("lineItems", lineItemsConnection(lineItemNode("9001",
                metafield("owner_telephone", "123"), metafield("owner_name", "同名用户"))));
        when(tokenService.getAccessToken(any())).thenReturn("old-token");
        stubPost(ordersResponse(nodes(node), null));
        SystemUser localOwner = SystemUser.builder().id(88L).companyId(100L).name("本公司用户").build();
        when(systemUserRepository.findByTelephoneAndCompanyIdWithDepartment(eq("123"), eq(100L), any(Pageable.class)))
                .thenReturn(telephoneMatches ? List.of(localOwner) : List.of());
        if (!telephoneMatches) {
            when(systemUserRepository.findByUserNameAndCompanyIdWithDepartment(eq("同名用户"), eq(100L), any(Pageable.class)))
                    .thenReturn(matchType == 2 ? List.of(localOwner) : List.of());
        }
        ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
        when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), eq(false))).thenReturn(true);

        ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO);

        assertEquals(1, result.getCreatedCount());
        assertEquals(matchType > 0 ? 88L : 1L, captor.getValue().getContextInfo().getSalesUid());
        verify(systemUserRepository, never()).findByTelephoneWithDepartment(anyString(), any());
        verify(systemUserRepository, never()).findByUserNameWithDepartment(anyString(), any());
        // 元字段随订单查询内联返回，只有一次请求
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    private ThirdPartyWebsiteDto buildWebsiteDto(String lastSyncOrderId) {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .nickName("TestShop")
                .handle("test-shop")
                .token("old-token")
                .tokenExpiresAt(LocalDateTime.now().plusHours(10))
                .clientId("client-id")
                .clientSecret("client-secret")
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .lastSyncOrderId(lastSyncOrderId)
                .owner(SystemUserDto.builder()
                        .id("1")
                        .companyId(100L)
                        .name("张三")
                        .departmentId(10L)
                        .departmentName("COD一部")
                        .build())
                .build();
    }

    private JSONObject money(String amount, String currency) {
        JSONObject money = new JSONObject();
        money.set("amount", amount);
        money.set("currency_code", currency);
        return money;
    }

    private JSONObject moneySet(String shopAmount, String presentmentAmount) {
        JSONObject set = new JSONObject();
        set.set("shop_money", money(shopAmount, "USD"));
        set.set("presentment_money", money(presentmentAmount, "EUR"));
        return set;
    }

    /**
     * REST 形状订单（与 webhook payload 同形），转换器测试通过 importWebhookOrder 输入
     */
    private JSONObject buildShopifyOrder() {
        JSONObject order = new JSONObject();
        order.set("id", "5500000000001");
        order.set("name", "#1001");
        order.set("created_at", "2026-06-01T10:30:00-04:00");
        order.set("landing_site", "/products/test");
        order.set("currency", "USD");
        order.set("presentment_currency", "EUR");
        order.set("financial_status", "paid");
        order.set("note", "请尽快发货");
        order.set("email", "order@example.com");
        order.set("total_price_set", moneySet("99.99", "89.99"));
        order.set("total_discounts_set", moneySet("10.00", "9.00"));
        order.set("total_tax_set", moneySet("5.00", "4.50"));

        JSONObject shippingAddress = new JSONObject();
        shippingAddress.set("first_name", "John");
        shippingAddress.set("last_name", "Doe");
        shippingAddress.set("phone", "+1234567890");
        shippingAddress.set("province", "California");
        shippingAddress.set("city", "Los Angeles");
        shippingAddress.set("address1", "123 Main St");
        shippingAddress.set("address2", "Apt 4B");
        shippingAddress.set("zip", "90001");
        shippingAddress.set("country_code", "US");
        order.set("shipping_address", shippingAddress);

        JSONObject clientDetails = new JSONObject();
        clientDetails.set("browser_ip", "192.168.1.1");
        clientDetails.set("user_agent", "Mozilla/5.0");
        order.set("client_details", clientDetails);

        JSONArray shippingLines = new JSONArray();
        JSONObject shippingLine = new JSONObject();
        shippingLine.set("price_set", moneySet("5.99", "5.49"));
        shippingLines.add(shippingLine);
        order.set("shipping_lines", shippingLines);

        JSONArray lineItems = new JSONArray();
        JSONObject item = new JSONObject();
        item.set("title", "Test Product");
        item.set("variant_title", "Red / L");
        item.set("price_set", moneySet("49.99", "44.99"));
        item.set("quantity", 2);
        item.set("sku", "SKU-001");
        lineItems.add(item);
        order.set("line_items", lineItems);
        return order;
    }

    // ==================== GraphQL 响应构造 ====================

    private JSONObject bareNode(String id, String createdAt) {
        return new JSONObject()
                .set("id", "gid://shopify/Order/" + id)
                .set("legacyResourceId", id)
                .set("createdAt", createdAt);
    }

    private JSONObject metafield(String key, String value) {
        return new JSONObject().set("namespace", "xyz").set("key", key).set("value", value);
    }

    private JSONObject lineItemNode(String productId, JSONObject... metafields) {
        JSONArray metafieldNodes = new JSONArray();
        for (JSONObject metafield : metafields) {
            metafieldNodes.add(metafield);
        }
        return new JSONObject()
                .set("id", "gid://shopify/LineItem/1" + productId)
                .set("title", "Test Product")
                .set("sku", "SKU-001")
                .set("quantity", 1)
                .set("currentQuantity", 1)
                .set("product", new JSONObject().set("legacyResourceId", productId)
                        .set("metafields", new JSONObject().set("nodes", metafieldNodes)));
    }

    private JSONObject lineItemsConnection(JSONObject... items) {
        return lineItemsConnection(null, items);
    }

    private JSONObject lineItemsConnection(String endCursor, JSONObject... items) {
        JSONArray array = new JSONArray();
        for (JSONObject item : items) {
            array.add(item);
        }
        return new JSONObject()
                .set("pageInfo", new JSONObject().set("hasNextPage", endCursor != null).set("endCursor", endCursor))
                .set("nodes", array);
    }

    private JSONArray nodes(JSONObject... orders) {
        JSONArray array = new JSONArray();
        for (JSONObject order : orders) {
            array.add(order);
        }
        return array;
    }

    /**
     * orders 连接响应，endCursor 非空表示还有下一页
     */
    private ResponseEntity<String> ordersResponse(JSONArray orders, String endCursor) {
        JSONObject connection = new JSONObject()
                .set("pageInfo", new JSONObject().set("hasNextPage", endCursor != null).set("endCursor", endCursor))
                .set("nodes", orders);
        return ResponseEntity.ok(new JSONObject().set("data", new JSONObject().set("orders", connection)).toString());
    }

    @SafeVarargs
    private void stubPost(ResponseEntity<String> first, ResponseEntity<String>... rest) {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn(first, rest);
    }

    private ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor() {
        return ArgumentCaptor.forClass(HttpEntity.class);
    }

    private static String query(HttpEntity<Map<String, Object>> entity) {
        return (String) entity.getBody().get("query");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> variables(HttpEntity<Map<String, Object>> entity) {
        return (Map<String, Object>) entity.getBody().get("variables");
    }

    private SyncThirdPartyOrdersRequest buildRequest(LocalDateTime createAtMin) {
        SyncThirdPartyOrdersRequest request = new SyncThirdPartyOrdersRequest();
        request.setId("1");
        request.setCreateAtMin(createAtMin);
        return request;
    }

    private void setId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ignored) {}
    }

    private HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
    }

    private HttpClientErrorException unavailableShop(int statusCode) {
        return HttpClientErrorException.create(HttpStatus.valueOf(statusCode), "Shop unavailable", HttpHeaders.EMPTY,
                "{\"errors\":\"Unavailable Shop\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("店铺冻结或锁定")
    class UnavailableShop {
        @ParameterizedTest
        @ValueSource(ints = {402, 423})
        void shouldDisableUnavailableShopWithoutRetryOrCursorUpdate(int statusCode) {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            HttpClientErrorException error = unavailableShop(statusCode);
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class))).thenThrow(error);

            assertSame(error, assertThrows(HttpClientErrorException.class,
                    () -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO)));

            verify(websiteStore).markWebsiteAuthError(eq(1L), contains("HTTP " + statusCode));
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
            verify(tokenService, never()).forceRefresh(any(), any());
            verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }

        @ParameterizedTest
        @ValueSource(ints = {402, 423})
        void shouldDisableShopWhenTokenAcquisitionFails(int statusCode) {
            when(tokenService.getAccessToken(any())).thenThrow(unavailableShop(statusCode));

            assertThrows(HttpClientErrorException.class,
                    () -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.MANUAL));

            verify(websiteStore).markWebsiteAuthError(eq(1L), contains("HTTP " + statusCode));
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
            verifyNoInteractions(restTemplate);
        }

        @ParameterizedTest
        @ValueSource(ints = {402, 423})
        void shouldDisableShopWhenCountingOrders(int statusCode) {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder().id(1L).handle("test-shop").build();
            when(websiteStore.getDtoById(1L)).thenReturn(buildWebsiteDto(null));
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(unavailableShop(statusCode));

            assertThrows(HttpClientErrorException.class,
                    () -> service.countOrders(website, new CountThirdPartyOrdersRequest()));

            verify(websiteStore).markWebsiteAuthError(eq(1L), contains("HTTP " + statusCode));
            verify(tokenService, never()).forceRefresh(any(), any());
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 404, 422})
        void shouldNotDisableShopForUnrelatedClientErrors(int statusCode) {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new HttpClientErrorException(HttpStatus.valueOf(statusCode)));

            assertThrows(HttpClientErrorException.class,
                    () -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO));

            verify(websiteStore, never()).markWebsiteAuthError(any(), any());
        }
    }

    // ==================== 计数 ====================

    @Test
    @DisplayName("计数：ordersCount 携带 UTC 的 created_at 范围且不设上限")
    void shouldCountOrdersWithCreatedAtRange() {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder().id(1L).handle("test-shop").build();
        when(websiteStore.getDtoById(1L)).thenReturn(buildWebsiteDto(null));
        when(tokenService.getAccessToken(any())).thenReturn("old-token");
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
        when(restTemplate.postForEntity(eq(GRAPHQL_URL), entities.capture(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"data\":{\"ordersCount\":{\"count\":12,\"precision\":\"EXACT\"}}}"));
        CountThirdPartyOrdersRequest request = new CountThirdPartyOrdersRequest();
        request.setCreateAtMin(LocalDateTime.of(2026, 1, 1, 8, 0));
        request.setCreateAtMax(LocalDateTime.of(2026, 1, 31, 8, 0));

        assertEquals(12, service.countOrders(website, request).getCount());

        assertTrue(query(entities.getValue()).contains("ordersCount(query: $q, limit: null)"));
        assertEquals("created_at:>='2026-01-01T00:00:00Z' created_at:<='2026-01-31T00:00:00Z'",
                variables(entities.getValue()).get("q"));
    }

    // ==================== 请求参数测试 ====================

    @Nested
    @DisplayName("loadOrders 请求参数")
    class LoadOrdersRequest {

        @Test
        @DisplayName("自动同步：按 created_at 游标升序查询 GraphQL，lastSyncOrderId 不作查询条件")
        void shouldUseSinceIdWhenAutoSyncWithCursor() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(urlCaptor.capture(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(), null));

            service.loadOrders(buildWebsiteDto("5500000000000"), buildRequest(LocalDateTime.of(2026, 6, 1, 8, 0)), "", SyncMode.AUTO);

            assertEquals(GRAPHQL_URL, urlCaptor.getValue());
            String query = query(entities.getValue());
            Map<String, Object> variables = variables(entities.getValue());
            assertTrue(query.contains("sortKey: CREATED_AT, reverse: false"));
            assertEquals("created_at:>='2026-06-01T00:00:00Z'", variables.get("q"));
            assertFalse(String.valueOf(variables.get("q")).contains("5500000000000"));
            assertEquals(ShopifyOrderSyncService.ORDER_PAGE_SIZE, variables.get("first"));
            assertNull(variables.get("cursor"));
            assertEquals("old-token", entities.getValue().getHeaders().getFirst("X-Shopify-Access-Token"));
            verify(websiteStore).updateLastSyncInfo(eq(1L), any());
        }

        @Test
        @DisplayName("手动同步：按 created_at 升序，不更新自动同步游标")
        void shouldOrderByCreatedAtWhenManualSync() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(), null));

            service.loadOrders(buildWebsiteDto("5500000000000"), buildRequest(null), "", SyncMode.MANUAL);

            assertTrue(query(entities.getValue()).contains("sortKey: CREATED_AT, reverse: false"));
            assertNull(variables(entities.getValue()).get("q"));
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
        }

        @Test
        @DisplayName("翻页：携带 after 游标与相同过滤条件，返回 endCursor 作为下一页")
        void shouldOnlySendPageInfoWhenPaging() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(), "next2"))
                    .thenReturn(ordersResponse(new JSONArray(), null));

            ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null),
                    buildRequest(LocalDateTime.of(2026, 6, 1, 8, 0)), "page1", SyncMode.MANUAL);

            assertEquals("page1", variables(entities.getValue()).get("cursor"));
            assertEquals("created_at:>='2026-06-01T00:00:00Z'", variables(entities.getValue()).get("q"));
            assertEquals("next2", result.getNextPageInfo());
            assertNull(service.loadOrders(buildWebsiteDto(null), buildRequest(null), "next2", SyncMode.MANUAL).getNextPageInfo());
        }

        @Test
        @DisplayName("行项目超过首屏数量时按游标补查并合并")
        void shouldFetchRemainingLineItems() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            JSONObject node = bareNode("5500000000001", "2026-06-01T10:30:00-04:00");
            node.set("lineItems", lineItemsConnection("li-cursor", lineItemNode("9001")));
            JSONObject remaining = lineItemsConnection(lineItemNode("9002"));
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(nodes(node), null))
                    .thenReturn(ResponseEntity.ok(new JSONObject().set("data", new JSONObject()
                            .set("order", new JSONObject().set("lineItems", remaining))).toString()));
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), eq(true))).thenReturn(true);

            service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.MANUAL);

            assertEquals(2, captor.getValue().getItemInfos().size());
            HttpEntity<Map<String, Object>> followUp = entities.getAllValues().get(1);
            assertTrue(query(followUp).contains("order(id: $id)"));
            assertEquals("gid://shopify/Order/5500000000001", variables(followUp).get("id"));
            assertEquals("li-cursor", variables(followUp).get("cursor"));
        }

        @Test
        @DisplayName("THROTTLED 时经客户端退避后成功")
        void shouldRetryAfterThrottled() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ResponseEntity.ok("{\"errors\":[{\"message\":\"Throttled\",\"extensions\":{\"code\":\"THROTTLED\"}}],"
                            + "\"extensions\":{\"cost\":{\"requestedQueryCost\":900,\"throttleStatus\":{\"maximumAvailable\":2000,"
                            + "\"currentlyAvailable\":100,\"restoreRate\":100}}}}"),
                    ordersResponse(nodes(bareNode("10", "2026-01-01T10:00:00+08:00")), null));
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(false))).thenReturn(true);

            ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO);

            assertEquals(1, result.getCreatedCount());
            verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }
    }

    // ==================== token 刷新测试 ====================

    @Nested
    @DisplayName("token 被拒绝")
    class TokenRejected {

        @Test
        @DisplayName("401 时刷新 token 并用新 token 重试")
        void shouldRefreshTokenAndRetryWhenUnauthorized() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(tokenService.forceRefresh(1L, "old-token")).thenReturn("new-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenThrow(unauthorized())
                    .thenReturn(ordersResponse(new JSONArray(), null));

            assertDoesNotThrow(() -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO));

            assertEquals("new-token", entities.getAllValues().get(1).getHeaders().getFirst("X-Shopify-Access-Token"));
            verify(websiteStore, never()).markWebsiteAuthError(any(), any());
        }

        @Test
        @DisplayName("刷新后仍 401 时应标记商城授权异常")
        void shouldMarkAuthErrorWhenStillUnauthorized() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(tokenService.forceRefresh(1L, "old-token")).thenReturn("new-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(unauthorized());

            assertThrows(HttpClientErrorException.class,
                    () -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO));

            verify(websiteStore).markWebsiteAuthError(eq(1L), anyString());
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
        }
    }

    // ==================== 订单转换测试 ====================

    @Nested
    @DisplayName("订单转换")
    class ConvertOrder {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("Shopify 浏览器 IP 缺失时使用 EasySell 附加详情的 IP")
        void shouldFallBackToNoteAttributeIp(boolean hasClientDetails) {
            JSONObject order = buildShopifyOrder();
            order.set("browser_ip", " ");
            if (hasClientDetails) {
                order.getJSONObject("client_details").set("browser_ip", "");
            } else {
                order.remove("client_details");
            }
            JSONArray attributes = new JSONArray();
            attributes.add(new JSONObject().set("name", "IP Address").set("value", "179.255.146.65"));
            order.set("note_attributes", attributes);

            assertEquals("179.255.146.65", loadAndCapture(buildWebsiteDto(null), order).getRiskInfo().getRemoteIp());
        }

        @Test
        @DisplayName("附加详情的 IP 不覆盖 Shopify 浏览器 IP")
        void shouldPreferClientBrowserIpOverTopLevelAndNoteAttribute() {
            JSONObject order = buildShopifyOrder();
            order.set("browser_ip", "8.8.8.8");
            JSONArray attributes = new JSONArray();
            attributes.add(new JSONObject().set("name", "IP Address").set("value", "179.255.146.65"));
            order.set("note_attributes", attributes);

            assertEquals("192.168.1.1", loadAndCapture(buildWebsiteDto(null), order).getRiskInfo().getRemoteIp());
        }

        @Test
        @DisplayName("client_details IP 为空时优先使用顶层 IP")
        void shouldPreferTopLevelBrowserIpOverNoteAttribute() {
            JSONObject order = buildShopifyOrder();
            order.set("browser_ip", "8.8.8.8");
            order.getJSONObject("client_details").set("browser_ip", " ");
            JSONArray attributes = new JSONArray();
            attributes.add(new JSONObject().set("name", "IP Address").set("value", "179.255.146.65"));
            order.set("note_attributes", attributes);

            assertEquals("8.8.8.8", loadAndCapture(buildWebsiteDto(null), order).getRiskInfo().getRemoteIp());
        }

        @Test
        @DisplayName("附加详情跳过空值，兼容字段名大小写和首尾空格")
        void shouldSkipBlankIpAttributesAndTrimFallbackIp() {
            JSONObject order = buildShopifyOrder();
            order.remove("client_details");
            JSONArray attributes = new JSONArray();
            attributes.add(new JSONObject().set("name", "Other field").set("value", "8.8.8.8"));
            attributes.add(new JSONObject().set("name", "IP Address").set("value", " "));
            attributes.add(new JSONObject().set("name", " ip address ").set("value", " 179.255.146.65 "));
            order.set("note_attributes", attributes);

            assertEquals("179.255.146.65", loadAndCapture(buildWebsiteDto(null), order).getRiskInfo().getRemoteIp());
        }

        @Test
        @DisplayName("没有可用 IP 属性时仍留空")
        void shouldKeepIpEmptyWhenNoMatchingAttributeHasValue() {
            JSONObject order = buildShopifyOrder();
            order.remove("client_details");
            JSONArray attributes = new JSONArray();
            attributes.add(new JSONObject().set("name", "Other field").set("value", "8.8.8.8"));
            attributes.add(new JSONObject().set("name", "IP Address").set("value", ""));
            attributes.add(new JSONObject().set("name", "IP Address"));
            order.set("note_attributes", attributes);

            assertEquals("", loadAndCapture(buildWebsiteDto(null), order).getRiskInfo().getRemoteIp());
        }

        @Test
        void shouldPreferShippingCountryOverLocale() {
            JSONObject order = buildShopifyOrder();
            order.set("customer_locale", "en-US");
            order.getJSONObject("shipping_address").set("country_code", "CA");
            Country canada = Country.builder().code("CA").name("加拿大").build();
            setId(canada, 2L);
            when(countryService.getByCode("CA")).thenReturn(Optional.of(canada));

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals("CA", request.getContextInfo().getCountryCode());
            assertEquals(2L, request.getContextInfo().getCountryId());
            assertEquals("EN", request.getContextInfo().getLanguageCode());
            verify(countryService, never()).getByCode("US");
        }

        @Test
        void shouldKeepShippingCountryEvenWhenNotInCountryTable() {
            JSONObject order = buildShopifyOrder();
            order.set("customer_locale", "en-US");
            order.getJSONObject("shipping_address").set("country_code", "CA");
            order.set("billing_address", new JSONObject().set("country_code", "US"));

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals("CA", request.getContextInfo().getCountryCode());
            verify(countryService, never()).getByCode("US");
        }

        @Test
        void shouldFallBackToBillingCountryWhenShippingIsMissing() {
            JSONObject order = buildShopifyOrder();
            order.remove("shipping_address");
            order.set("customer_locale", "en-US");
            order.set("billing_address", new JSONObject().set("country_code", "CA"));
            assertEquals("CA", loadAndCapture(buildWebsiteDto(null), order).getContextInfo().getCountryCode());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("配送地址缺失或为空对象时，使用账单地址同步客户和地址信息")
        void shouldFallBackToBillingAddress(boolean emptyShippingAddress) {
            JSONObject order = buildShopifyOrder();
            JSONObject billingAddress = order.getJSONObject("shipping_address");
            billingAddress.set("first_name", "Billing");
            billingAddress.set("last_name", "Recipient");
            billingAddress.set("phone", "+18885551234");
            order.set("billing_address", billingAddress);
            if (emptyShippingAddress) {
                order.set("shipping_address", new JSONObject());
            } else {
                order.remove("shipping_address");
            }
            order.set("customer", new JSONObject().set("first_name", "Customer").set("phone", "OTHER"));

            var deliveryInfo = loadAndCapture(buildWebsiteDto(null), order).getDeliveryInfo();

            assertEquals("Billing", deliveryInfo.getFirstName());
            assertEquals("Recipient", deliveryInfo.getLastName());
            assertEquals("+18885551234", deliveryInfo.getPhone());
            assertEquals("California", deliveryInfo.getProvince());
            assertEquals("Los Angeles", deliveryInfo.getCity());
            assertEquals("90001", deliveryInfo.getPostalCode());
            assertEquals("123 Main St /Apt 4B", deliveryInfo.getAddress());
        }

        @Test
        @DisplayName("配送地址和账单地址同时存在时，使用配送地址")
        void shouldPreferShippingAddressOverBillingAddress() {
            JSONObject order = buildShopifyOrder();
            order.set("billing_address", new JSONObject()
                    .set("first_name", "Billing")
                    .set("last_name", "Recipient")
                    .set("phone", "OTHER")
                    .set("address1", "Different billing address"));

            var deliveryInfo = loadAndCapture(buildWebsiteDto(null), order).getDeliveryInfo();

            assertEquals("John", deliveryInfo.getFirstName());
            assertEquals("Doe", deliveryInfo.getLastName());
            assertEquals("+1234567890", deliveryInfo.getPhone());
            assertEquals("123 Main St /Apt 4B", deliveryInfo.getAddress());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        @DisplayName("配送地址和账单地址都不可用时，仍从 customer 获取基本信息")
        void shouldFallBackToCustomerWhenBothAddressesAreUnavailable(boolean emptyBillingAddress) {
            JSONObject order = buildShopifyOrder();
            order.remove("shipping_address");
            if (emptyBillingAddress) {
                order.set("billing_address", new JSONObject());
            }
            order.set("customer", new JSONObject()
                    .set("first_name", "Customer")
                    .set("last_name", "Name")
                    .set("phone", "+18885551234"));

            var deliveryInfo = loadAndCapture(buildWebsiteDto(null), order).getDeliveryInfo();

            assertEquals("Customer", deliveryInfo.getFirstName());
            assertEquals("Name", deliveryInfo.getLastName());
            assertEquals("+18885551234", deliveryInfo.getPhone());
            assertNull(deliveryInfo.getAddress());
        }

        @Test
        void shouldUseOrderedQuantityAndKeepRemovedProducts() {
            JSONObject order = buildShopifyOrder();
            JSONArray items = order.getJSONArray("line_items");
            items.getJSONObject(0).set("quantity", 3).set("current_quantity", 1);
            items.add(new JSONObject().set("quantity", 2).set("current_quantity", 0).set("sku", "REMOVED"));

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals(2, request.getItemInfos().size());
            assertEquals(3, request.getItemInfos().get(0).getQuantity());
            assertEquals(2, request.getItemInfos().get(1).getQuantity());
            assertEquals("REMOVED", request.getItemInfos().get(1).getSkuCode());
            verify(productSKUService).listBySkuCodes(eq(List.of("SKU-001", "REMOVED")), anyLong());
        }

        @Test
        void shouldUseOriginalAmountsInsteadOfCurrentAmounts() {
            JSONObject order = buildShopifyOrder();
            order.set("current_total_price_set", moneySet("0.00", "0.00"));
            order.set("current_total_discounts_set", moneySet("0.00", "0.00"));
            order.set("current_total_tax_set", moneySet("0.00", "0.00"));

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals(0, new BigDecimal("99.99").compareTo(request.getFinancialInfo().getTotalAmount()));
            assertEquals(0, new BigDecimal("10.00").compareTo(request.getFinancialInfo().getDiscountAmount()));
            assertEquals(0, new BigDecimal("5.00").compareTo(request.getFinancialInfo().getTaxAmount()));
        }

        @Test
        void shouldMarkCustomerCancelledWhenOrderCancelled() {
            JSONObject order = buildShopifyOrder();
            order.set("cancelled_at", "2026-06-02T10:00:00-04:00");
            assertEquals(Boolean.TRUE, loadAndCapture(buildWebsiteDto(null), order).getCustomerCancelled());
        }

        @Test
        void shouldMarkCustomerCancelledWhenAllItemsRemoved() {
            JSONObject order = buildShopifyOrder();
            order.getJSONArray("line_items").getJSONObject(0).set("current_quantity", 0);

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals(Boolean.TRUE, request.getCustomerCancelled());
            assertEquals(1, request.getItemInfos().size());
        }

        @Test
        void shouldNotMarkCustomerCancelledWhenSomeItemsRemain() {
            JSONObject order = buildShopifyOrder();
            JSONArray items = order.getJSONArray("line_items");
            items.getJSONObject(0).set("current_quantity", 1);
            items.add(new JSONObject().set("quantity", 2).set("current_quantity", 0).set("sku", "REMOVED"));
            assertEquals(Boolean.FALSE, loadAndCapture(buildWebsiteDto(null), order).getCustomerCancelled());
        }

        /**
         * REST 形状订单经 webhook 导入路径（updateExisting=true，与手动同步一致）转换
         */
        private EditTemporaryOrderRequest loadAndCapture(ThirdPartyWebsiteDto website, JSONObject order) {
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), anyBoolean())).thenReturn(true);

            service.importWebhookOrder(website, order, true);
            return captor.getValue();
        }

        @Test
        @DisplayName("完整订单应按 Shopify 字段映射")
        void shouldConvertFullOrder() {
            Currency usd = Currency.builder().code("USD").symbol("$").name("美元").build();
            when(currencyService.getByCode("USD")).thenReturn(Optional.of(usd));
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());
            ProductSKU sku001 = ProductSKU.builder().skuCode("SKU-001").name("SKU-001").build();
            setId(sku001, 10L);
            when(productSKUService.listBySkuCodes(anyList(), anyLong())).thenReturn(List.of(sku001));

            EditTemporaryOrderRequest req = loadAndCapture(buildWebsiteDto(null), buildShopifyOrder());

            assertEquals(100L, req.getCompanyId());
            assertEquals("TestShop-SHOPIFY", req.getFrom());
            assertEquals(WebsiteTypeEnum.SHOPIFY, req.getPlatform());
            assertEquals("5500000000001", req.getOriginOrderId());
            // -04:00 的 10:30 换算为东八区 22:30
            assertEquals(LocalDateTime.of(2026, 6, 1, 22, 30), req.getOrderTime());

            assertEquals("John", req.getDeliveryInfo().getFirstName());
            assertEquals("123 Main St /Apt 4B", req.getDeliveryInfo().getAddress());
            assertEquals("请尽快发货", req.getDeliveryInfo().getRemark());
            // 没有 customer 时取订单上的 email
            assertEquals("order@example.com", req.getDeliveryInfo().getEmail());

            assertEquals(new BigDecimal("99.99"), req.getFinancialInfo().getTotalAmount());
            assertEquals(new BigDecimal("10.00"), req.getFinancialInfo().getDiscountAmount());
            assertEquals(new BigDecimal("5.00"), req.getFinancialInfo().getTaxAmount());
            assertEquals(new BigDecimal("5.99"), req.getFinancialInfo().getShippingFee());

            assertEquals(PaymentMethod.COD, req.getPaymentInfo().getPaymentMethod());
            assertEquals(PaymentStatus.PAID, req.getPaymentInfo().getPaymentStatus());

            assertEquals("USD", req.getContextInfo().getCurrencyCode());
            assertEquals("test-shop.myshopify.com", req.getContextInfo().getWebsiteUrl());
            assertEquals("US", req.getContextInfo().getCountryCode());

            assertEquals("192.168.1.1", req.getRiskInfo().getRemoteIp());

            assertEquals(1, req.getItemInfos().size());
            assertEquals("Test Product", req.getItemInfos().get(0).getTitle());
            assertEquals("Red / L", req.getItemInfos().get(0).getSpecTitle());
            assertEquals(new BigDecimal("49.99"), req.getItemInfos().get(0).getSellPrice());
            assertEquals(2, req.getItemInfos().get(0).getQuantity());
            assertEquals("SKU-001", req.getItemInfos().get(0).getSkuCode());
            assertEquals(10L, req.getItemInfos().get(0).getSkuId());
        }

        @Test
        @DisplayName("订单展示币种模式应取 presentment_money")
        void shouldUsePresentmentMoney() {
            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());
            ThirdPartyWebsiteDto website = buildWebsiteDto(null);
            website.setCurrencyMode(CurrencyMode.PRESENTMENT_MONEY);

            EditTemporaryOrderRequest req = loadAndCapture(website, buildShopifyOrder());

            assertEquals(new BigDecimal("89.99"), req.getFinancialInfo().getTotalAmount());
            assertEquals(new BigDecimal("5.49"), req.getFinancialInfo().getShippingFee());
            assertEquals("EUR", req.getContextInfo().getCurrencyCode());
            assertEquals(new BigDecimal("44.99"), req.getItemInfos().get(0).getSellPrice());
        }

        @Test
        @DisplayName("voided 状态按待支付处理")
        void shouldMapVoidedToWaitPay() {
            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());
            JSONObject order = buildShopifyOrder();
            order.set("financial_status", "voided");

            EditTemporaryOrderRequest req = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals(PaymentStatus.WAIT_PAY, req.getPaymentInfo().getPaymentStatus());
        }

        @Test
        @DisplayName("同一页内相同商品的元字段随订单内联返回，不逐商品请求，并用于补全 SKU 与品名")
        void shouldFetchMetafieldsOncePerProduct() {
            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());
            JSONObject order1 = bareNode("5500000000001", "2026-06-01T10:30:00-04:00");
            order1.set("lineItems", lineItemsConnection(lineItemNode("9001",
                    metafield("sku_code_high", "SKU-HIGH"), metafield("cn_product_name", "测试商品"))));
            JSONObject order2 = bareNode("5500000000002", "2026-06-01T10:31:00-04:00");
            order2.set("lineItems", lineItemsConnection(lineItemNode("9001",
                    metafield("sku_code_high", "SKU-HIGH"), metafield("cn_product_name", "测试商品"))));
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(nodes(order1, order2), null));
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), anyBoolean())).thenReturn(true);

            service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.MANUAL);

            // 只有 1 次订单列表请求
            verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
            assertEquals(2, captor.getAllValues().size());
            assertEquals("SKU-HIGH", captor.getAllValues().get(1).getItemInfos().get(0).getSkuCode());
            assertEquals("测试商品", captor.getAllValues().get(1).getItemInfos().get(0).getMerchandise());
        }

        @Test
        @DisplayName("webhook 订单的元字段缓存未命中时用 nodes 批量查询")
        void shouldBatchFetchMetafieldsForWebhookOrder() {
            JSONObject order = buildShopifyOrder();
            JSONArray items = order.getJSONArray("line_items");
            items.getJSONObject(0).set("product_id", 9001L);
            items.add(new JSONObject().set("quantity", 1).set("sku", "").set("product_id", 9002L));
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"data\":{\"nodes\":["
                            + "{\"id\":\"gid://shopify/Product/9001\",\"legacyResourceId\":\"9001\",\"metafields\":{\"nodes\":["
                            + "{\"namespace\":\"xyz\",\"key\":\"sku_code_high\",\"value\":\"SKU-HIGH\"}]}},"
                            + "{\"id\":\"gid://shopify/Product/9002\",\"legacyResourceId\":\"9002\",\"metafields\":{\"nodes\":["
                            + "{\"namespace\":\"xyz\",\"key\":\"sku_code\",\"value\":\"SKU-FALLBACK\"}]}}]}}"));
            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals("SKU-HIGH", request.getItemInfos().get(0).getSkuCode());
            assertEquals("SKU-FALLBACK", request.getItemInfos().get(1).getSkuCode());
            verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
            assertTrue(query(entities.getValue()).contains("nodes(ids: $ids)"));
            assertEquals(List.of("gid://shopify/Product/9001", "gid://shopify/Product/9002"), variables(entities.getValue()).get("ids"));
        }

        @Test
        @DisplayName("元字段查询失败时降级为空，订单照常导入")
        void shouldIgnoreMetafieldFailure() {
            JSONObject order = buildShopifyOrder();
            order.getJSONArray("line_items").getJSONObject(0).set("product_id", 9001L);
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"errors\":[{\"message\":\"boom\"}]}"));

            assertEquals("SKU-001", loadAndCapture(buildWebsiteDto(null), order).getItemInfos().get(0).getSkuCode());
        }
    }

    // ==================== webhook 订单导入 ====================

    @Nested
    @DisplayName("importWebhookOrder")
    class ImportWebhookOrder {

        @Test
        @DisplayName("updateExisting=false 且订单已存在：返回 false 且不改库")
        void shouldSkipExistingWhenNotUpdating() {
            when(temporaryOrderService.findByOriginOrderId("5500000000001")).thenReturn(Optional.of(TemporaryOrder.builder().build()));

            assertFalse(service.importWebhookOrder(buildWebsiteDto(null), buildShopifyOrder(), false));

            verify(temporaryOrderService, never()).synchronizeOrderFromExternalSystem(any(), anyBoolean());
        }

        @Test
        @DisplayName("updateExisting=false 且订单不存在：新建")
        void shouldCreateWhenMissing() {
            when(temporaryOrderService.findByOriginOrderId("5500000000001")).thenReturn(Optional.empty());
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(false))).thenReturn(true);

            assertTrue(service.importWebhookOrder(buildWebsiteDto(null), buildShopifyOrder(), false));
        }

        @Test
        @DisplayName("updateExisting=true：覆盖已存在订单，不存在则创建，返回是否新建")
        void shouldUpsertWhenUpdating() {
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(true))).thenReturn(false);

            assertFalse(service.importWebhookOrder(buildWebsiteDto(null), buildShopifyOrder(), true));

            verify(temporaryOrderService, never()).findByOriginOrderId(anyString());
            verify(temporaryOrderService).synchronizeOrderFromExternalSystem(any(), eq(true));
        }
    }

    // ==================== 对账 ====================

    @Nested
    @DisplayName("reconcileOrders")
    class Reconcile {

        private JSONObject updatedNode(String id, String updatedAt) {
            return bareNode(id, "2026-10-18T08:00:00+08:00").set("updatedAt", updatedAt);
        }

        @Test
        @DisplayName("新订单创建、已存在且已取消的标记取消、其余跳过，返回最大 updated_at")
        void shouldCreateMarkCancelledAndSkip() {
            JSONObject created = updatedNode("1001", "2026-10-18T10:00:00+08:00");
            JSONObject cancelled = updatedNode("1002", "2026-10-18T12:00:00+08:00").set("cancelledAt", "2026-10-18T12:00:00+08:00");
            JSONObject unchanged = updatedNode("1003", "2026-10-18T11:00:00+08:00");
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(nodes(created, cancelled), "page-2"))
                    .thenReturn(ordersResponse(nodes(unchanged), null));
            when(temporaryOrderService.findByOriginOrderId("1001")).thenReturn(Optional.empty());
            when(temporaryOrderService.findByOriginOrderId("1002")).thenReturn(Optional.of(TemporaryOrder.builder().build()));
            when(temporaryOrderService.findByOriginOrderId("1003")).thenReturn(Optional.of(TemporaryOrder.builder().build()));
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), eq(false))).thenReturn(true);
            when(temporaryOrderService.markCustomerCancelled("1002")).thenReturn(true);

            LocalDateTime cursor = service.reconcileOrders(buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0));

            assertEquals(LocalDateTime.of(2026, 10, 18, 12, 0), cursor);
            assertEquals("1001", captor.getValue().getOriginOrderId());
            verify(temporaryOrderService, times(1)).synchronizeOrderFromExternalSystem(any(), anyBoolean());
            verify(temporaryOrderService).markCustomerCancelled("1002");
            verify(temporaryOrderService, never()).markCustomerCancelled("1003");
            HttpEntity<Map<String, Object>> first = entities.getAllValues().get(0);
            assertTrue(query(first).contains("sortKey: UPDATED_AT"));
            assertEquals("updated_at:>='2026-10-18T00:00:00Z'", variables(first).get("q"));
            assertEquals("page-2", variables(entities.getAllValues().get(1)).get("cursor"));
            assertEquals("updated_at:>='2026-10-18T00:00:00Z'", variables(entities.getAllValues().get(1)).get("q"));
        }

        @Test
        @DisplayName("无订单时返回 since")
        void shouldReturnSinceWhenNoOrders() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(new JSONArray(), null));

            LocalDateTime since = LocalDateTime.of(2026, 10, 18, 8, 0);
            assertEquals(since, service.reconcileOrders(buildWebsiteDto(null), since));
        }

        @Test
        @DisplayName("处理失败时停在失败单之前的游标")
        void shouldStopAtFailure() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(nodes(updatedNode("1001", "2026-10-18T10:00:00+08:00"),
                    updatedNode("1002", "2026-10-18T11:00:00+08:00"),
                    updatedNode("1003", "2026-10-18T12:00:00+08:00")), "more"));
            when(temporaryOrderService.findByOriginOrderId(anyString())).thenReturn(Optional.empty());
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(false)))
                    .thenReturn(true).thenThrow(new RuntimeException("save failed"));

            assertEquals(LocalDateTime.of(2026, 10, 18, 10, 0),
                    service.reconcileOrders(buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0)));
            verify(temporaryOrderService, times(2)).synchronizeOrderFromExternalSystem(any(), anyBoolean());
            verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }
    }

    // ==================== 游标策略测试 ====================

    @Nested
    @DisplayName("游标策略")
    class Cursor {

        @Test
        void shouldContinueManualPageAfterFailureAndPreserveNextPage() {
            JSONArray orders = new JSONArray();
            for (String id : List.of("10", "20", "30")) {
                orders.add(bareNode(id, "2026-01-01T10:00:00+08:00"));
            }
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(orders, "next"));
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(true)))
                    .thenReturn(true).thenThrow(new RuntimeException("save failed")).thenReturn(true);

            ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.MANUAL);

            assertEquals(3, result.getFetchedCount());
            assertEquals(2, result.getSuccessCount());
            assertEquals(1, result.getFailedCount());
            assertEquals("next", result.getNextPageInfo());
            ArgumentCaptor<EditTemporaryOrderRequest> requests = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            verify(temporaryOrderService, times(3)).synchronizeOrderFromExternalSystem(requests.capture(), eq(true));
            assertEquals("30", requests.getAllValues().get(2).getOriginOrderId());
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
        }

        @Test
        void shouldKeepCursorEmptyWhenFirstAutomaticOrderFails() {
            JSONArray orders = new JSONArray();
            orders.add(bareNode("10", "2026-01-01T10:00:00+08:00"));
            orders.add(bareNode("20", "2026-01-01T11:00:00+08:00"));
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(false)))
                    .thenThrow(new RuntimeException("save failed"));

            ShoplineOrderLoadResult result = loadAuto(orders);

            assertNull(result.getCursorOrderId());
            assertNull(result.getCursorOrderTime());
            assertEquals(1, result.getFailedCount());
            verify(temporaryOrderService, times(1)).synchronizeOrderFromExternalSystem(any(), eq(false));
        }

        private ShoplineOrderLoadResult loadAuto(JSONArray orders) {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(orders, null));
            return service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO);
        }

        @Test
        @DisplayName("已存在的订单跳过，游标仍推进")
        void shouldSkipExistingOrders() {
            JSONArray orders = new JSONArray();
            orders.add(bareNode("10", "2026-01-01T10:00:00+08:00"));
            orders.add(bareNode("30", "2026-01-01T12:00:00+08:00"));
            when(temporaryOrderService.findByOriginOrderId(anyString()))
                    .thenReturn(Optional.of(TemporaryOrder.builder().build()));

            ShoplineOrderLoadResult result = loadAuto(orders);

            assertEquals("30", result.getCursorOrderId());
            assertEquals(2, result.getSkippedCount());
            assertEquals(0, result.getCreatedCount());
            verify(temporaryOrderService, never()).synchronizeOrderFromExternalSystem(any(), anyBoolean());
        }

        @Test
        @DisplayName("自动同步整页都已存在且有下一页时继续翻页，游标越过边界")
        void shouldPageThroughFullySkippedAutoPage() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(nodes(bareNode("10", "2026-01-01T10:00:00+08:00"),
                            bareNode("20", "2026-01-01T10:00:00+08:00")), "page-2"))
                    .thenReturn(ordersResponse(nodes(bareNode("30", "2026-01-01T11:00:00+08:00")), null));
            when(temporaryOrderService.findByOriginOrderId("10")).thenReturn(Optional.of(TemporaryOrder.builder().build()));
            when(temporaryOrderService.findByOriginOrderId("20")).thenReturn(Optional.of(TemporaryOrder.builder().build()));
            when(temporaryOrderService.findByOriginOrderId("30")).thenReturn(Optional.empty());
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), eq(false))).thenReturn(true);

            ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null),
                    buildRequest(LocalDateTime.of(2026, 1, 1, 10, 0)), "", SyncMode.AUTO);

            assertEquals(3, result.getFetchedCount());
            assertEquals(2, result.getSkippedCount());
            assertEquals(1, result.getCreatedCount());
            assertEquals("30", result.getCursorOrderId());
            assertEquals(LocalDateTime.of(2026, 1, 1, 11, 0), result.getCursorOrderTime());
            assertNull(result.getNextPageInfo());
            assertEquals("page-2", variables(entities.getAllValues().get(1)).get("cursor"));
            verify(websiteStore, times(1)).updateLastSyncInfo(eq(1L), same(result));
        }

        @Test
        @DisplayName("中间出现失败单：立即终止本页，游标停在失败单前")
        void shouldStopAtFailure() {
            JSONArray orders = new JSONArray();
            orders.add(bareNode("10", "2026-01-01T10:00:00+08:00"));
            orders.add(bareNode("20", "2026-01-01T11:00:00+08:00"));
            orders.add(bareNode("30", "2026-01-01T12:00:00+08:00"));
            when(temporaryOrderService.findByOriginOrderId(anyString())).thenReturn(Optional.empty());
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(any(), anyBoolean()))
                    .thenReturn(true)
                    .thenThrow(new RuntimeException("模拟转换失败"));

            ShoplineOrderLoadResult result = loadAuto(orders);

            assertEquals("10", result.getCursorOrderId());
            assertEquals(10, result.getCursorOrderTime().getHour());
            assertEquals(1, result.getCreatedCount());
            assertEquals(1, result.getFailedCount());
            verify(temporaryOrderService, times(2)).synchronizeOrderFromExternalSystem(any(), anyBoolean());
        }
    }

    // ==================== 取消状态检查测试 ====================

    @Nested
    @DisplayName("checkCancelledOrders")
    class CancelCheck {

        private JSONObject cancelCheckOrder(String id, String updatedAt) {
            JSONArray lineItems = new JSONArray();
            lineItems.add(new JSONObject().set("currentQuantity", 1));
            return new JSONObject().set("id", "gid://shopify/Order/" + id).set("legacyResourceId", id).set("updatedAt", updatedAt)
                    .set("lineItems", new JSONObject().set("pageInfo", new JSONObject().set("hasNextPage", false)).set("nodes", lineItems));
        }

        @Test
        void shouldMarkOnlyCancelledOrdersAndReturnLatestUpdatedAt() {
            JSONArray orders = new JSONArray();
            orders.add(cancelCheckOrder("1001", "2026-10-18T10:00:00+08:00").set("cancelledAt", "2026-10-18T10:00:00+08:00"));
            orders.add(cancelCheckOrder("1002", "2026-10-18T11:00:00+08:00"));
            JSONObject removed = cancelCheckOrder("1003", "2026-10-18T09:00:00+08:00");
            removed.getJSONObject("lineItems").getJSONArray("nodes").getJSONObject(0).set("currentQuantity", 0);
            orders.add(removed);

            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(orders, null));
            when(temporaryOrderService.markCustomerCancelled(anyString())).thenReturn(true);

            LocalDateTime cursor = service.checkCancelledOrders(buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0), null).cursor();

            assertEquals(LocalDateTime.of(2026, 10, 18, 11, 0), cursor);
            verify(temporaryOrderService).markCustomerCancelled("1001");
            verify(temporaryOrderService).markCustomerCancelled("1003");
            verify(temporaryOrderService, never()).markCustomerCancelled("1002");
            String query = query(entities.getValue());
            Map<String, Object> variables = variables(entities.getValue());
            assertEquals("updated_at:>='2026-10-18T00:00:00Z'", variables.get("q"));
            assertTrue(query.contains("sortKey: UPDATED_AT"));
            assertTrue(query.contains("cancelledAt updatedAt"));
            assertFalse(query.contains("metafields"));
            assertEquals(ShopifyOrderSyncService.CANCEL_CHECK_PAGE_SIZE, variables.get("first"));
            verifyNoInteractions(productSKUService);
        }

        @Test
        void shouldFollowNextPage() {
            JSONArray firstPage = new JSONArray();
            firstPage.add(cancelCheckOrder("1001", "2026-10-18T10:00:00+08:00"));
            JSONArray secondPage = new JSONArray();
            secondPage.add(cancelCheckOrder("1002", "2026-10-18T12:00:00+08:00"));

            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(anyString(), entities.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(firstPage, "next-page"))
                    .thenReturn(ordersResponse(secondPage, null));

            LocalDateTime cursor = service.checkCancelledOrders(buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0), null).cursor();

            assertEquals(LocalDateTime.of(2026, 10, 18, 12, 0), cursor);
            List<HttpEntity<Map<String, Object>>> requests = entities.getAllValues();
            assertEquals(2, requests.size());
            assertNull(variables(requests.get(0)).get("cursor"));
            assertEquals("next-page", variables(requests.get(1)).get("cursor"));
            verify(temporaryOrderService, never()).markCustomerCancelled(anyString());
        }

        @Test
        void shouldKeepBoundaryWhenNoOrdersUpdated() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(new JSONArray(), null));

            LocalDateTime start = LocalDateTime.of(2026, 10, 18, 8, 0);
            assertEquals(new ShopifyCancelCheckResult(start, null), service.checkCancelledOrders(buildWebsiteDto(null), start, null));
        }

        @Test
        void shouldResumeBeyondPageLimitWhenAllOrdersShareTimestamp() {
            int pageSize = ShopifyOrderSyncService.CANCEL_CHECK_PAGE_SIZE;
            int maxPages = ShopifyOrderSyncService.CANCEL_CHECK_MAX_PAGES;
            int total = pageSize * maxPages + 1;
            LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 18, 10, 0);
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenAnswer(invocation -> {
                        HttpEntity<Map<String, Object>> entity = invocation.getArgument(1);
                        Object cursor = variables(entity).get("cursor");
                        int page = cursor == null ? 0 : Integer.parseInt(String.valueOf(cursor));
                        JSONArray orders = new JSONArray();
                        for (int i = page * pageSize; i < Math.min((page + 1) * pageSize, total); i++) {
                            orders.add(cancelCheckOrder(String.valueOf(i), "2026-10-18T10:00:00+08:00")
                                    .set("cancelledAt", "2026-10-18T10:00:00+08:00"));
                        }
                        return ordersResponse(orders, page < maxPages ? String.valueOf(page + 1) : null);
                    });

            ShopifyCancelCheckResult first = service.checkCancelledOrders(buildWebsiteDto(null), updatedAt.minusHours(1), null);
            assertEquals(new ShopifyCancelCheckResult(updatedAt, String.valueOf(maxPages)), first);
            verify(temporaryOrderService, times(total - 1)).markCustomerCancelled(anyString());
            verify(temporaryOrderService, never()).markCustomerCancelled(String.valueOf(total - 1));

            ShopifyCancelCheckResult second = service.checkCancelledOrders(buildWebsiteDto(null), first.cursor(), first.nextPageInfo());
            assertEquals(new ShopifyCancelCheckResult(updatedAt, null), second);
            verify(temporaryOrderService).markCustomerCancelled(String.valueOf(total - 1));
            verify(temporaryOrderService, times(total)).markCustomerCancelled(anyString());
        }

        @Test
        void shouldFailWithoutReturningProgressWhenContinuationPageFails() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(new JSONArray(), "next"), ResponseEntity.ok("{\"data\":{\"orders\":null}}"));

            assertThrows(IllegalStateException.class, () -> service.checkCancelledOrders(
                    buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0), "saved-page"));
        }

        @Test
        @DisplayName("首屏行项目剩余数量全为 0 且还有更多行项目时补查后再判断")
        void shouldFetchRemainingLineItemsBeforeJudgingRemoval() {
            JSONObject order = cancelCheckOrder("1001", "2026-10-18T10:00:00+08:00");
            order.getJSONObject("lineItems").set("pageInfo", new JSONObject().set("hasNextPage", true).set("endCursor", "li"));
            order.getJSONObject("lineItems").getJSONArray("nodes").getJSONObject(0).set("currentQuantity", 0);
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            stubPost(ordersResponse(nodes(order), null),
                    ResponseEntity.ok("{\"data\":{\"order\":{\"lineItems\":{\"pageInfo\":{\"hasNextPage\":false},"
                            + "\"nodes\":[{\"currentQuantity\":1}]}}}}"));

            service.checkCancelledOrders(buildWebsiteDto(null), LocalDateTime.of(2026, 10, 18, 8, 0), null);

            verify(temporaryOrderService, never()).markCustomerCancelled(anyString());
            verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        }
    }

    // ==================== 授权验证测试 ====================

    @Nested
    @DisplayName("verifyAndUpdateAuthStatus")
    class VerifyAuthStatus {

        @ParameterizedTest
        @ValueSource(ints = {402, 423})
        void shouldMarkUnavailableShopWhenVerifyingOrdersApi(int statusCode) {
            when(tokenService.fetchToken(anyString(), anyString(), anyString()))
                    .thenReturn(new ShopifyTokenService.ShopifyToken("new-token", LocalDateTime.now().plusHours(24)));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(unavailableShop(statusCode));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertEquals(StatusEnum.INVALID, website.getStatus());
            assertTrue(website.getAuthMessage().contains("HTTP " + statusCode));
            assertTrue(website.getAuthMessage().contains(statusCode == 402 ? "冻结" : "锁定"));
        }

        @ParameterizedTest
        @ValueSource(ints = {402, 423})
        void shouldReportUnavailableShopWhenFetchingToken(int statusCode) {
            when(tokenService.fetchToken(anyString(), anyString(), anyString()))
                    .thenThrow(unavailableShop(statusCode));

            BaseException error = assertThrows(BaseException.class,
                    () -> service.verifyAndUpdateAuthStatus(buildWebsite()));

            assertTrue(error.getMessage().contains("HTTP " + statusCode));
            assertTrue(error.getMessage().contains(statusCode == 402 ? "冻结" : "锁定"));
            verifyNoInteractions(restTemplate);
        }

        private ThirdPartyWebsite buildWebsite() {
            return ThirdPartyWebsite.builder()
                    .handle("test-shop")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .websiteType(WebsiteTypeEnum.SHOPIFY)
                    .build();
        }

        @Test
        @DisplayName("换取 token 成功且 GraphQL ordersCount 可用时应设置 AUTHED 并写入 token")
        void shouldSetAuthedWhenCredentialsValid() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            when(tokenService.fetchToken("test-shop", "client-id", "client-secret"))
                    .thenReturn(new ShopifyTokenService.ShopifyToken("new-token", expiresAt));
            ArgumentCaptor<HttpEntity<Map<String, Object>>> entities = entityCaptor();
            when(restTemplate.postForEntity(eq(GRAPHQL_URL), entities.capture(), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"data\":{\"ordersCount\":{\"count\":5,\"precision\":\"EXACT\"}}}"));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, website.getAuthStatus());
            assertEquals("new-token", website.getToken());
            assertEquals(expiresAt, website.getTokenExpiresAt());
            assertTrue(query(entities.getValue()).contains("ordersCount"));
            assertEquals("new-token", entities.getValue().getHeaders().getFirst("X-Shopify-Access-Token"));
        }

        @Test
        @DisplayName("换取 token 失败时应抛出异常拒绝保存")
        void shouldThrowWhenFetchTokenFails() {
            when(tokenService.fetchToken(anyString(), anyString(), anyString())).thenThrow(unauthorized());

            assertThrows(BaseException.class, () -> service.verifyAndUpdateAuthStatus(buildWebsite()));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("缺少订单权限(403)时应设置 ERROR")
        void shouldSetErrorWhenForbidden() {
            when(tokenService.fetchToken(anyString(), anyString(), anyString()))
                    .thenReturn(new ShopifyTokenService.ShopifyToken("new-token", LocalDateTime.now().plusHours(24)));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertTrue(website.getAuthMessage().contains("read_orders"));
        }

        @Test
        @DisplayName("GraphQL 返回 ACCESS_DENIED 时应设置 ERROR 并提示缺少 read_orders")
        void shouldSetErrorWhenAccessDenied() {
            when(tokenService.fetchToken(anyString(), anyString(), anyString()))
                    .thenReturn(new ShopifyTokenService.ShopifyToken("new-token", LocalDateTime.now().plusHours(24)));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("{\"errors\":[{\"message\":\"Access denied for ordersCount field.\","
                            + "\"extensions\":{\"code\":\"ACCESS_DENIED\"}}]}"));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertTrue(website.getAuthMessage().contains("read_orders"));
        }
    }
}
