package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyOrderSyncServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ShopifyTokenService tokenService;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Mock private ICurrencyService currencyService;
    @Mock private ILanguageService languageService;
    @Mock private ICountryService countryService;
    @Mock private ITemporaryOrderService temporaryOrderService;
    @Mock private IProductSKUService productSKUService;
    @Mock private SystemUserRepository systemUserRepository;

    @InjectMocks
    private ShopifyOrderSyncService service;

    @Test
    void shouldRejectUnsafeStoredHandleBeforeSendingToken() {
        ThirdPartyWebsiteDto website = buildWebsiteDto(null);
        website.setHandle("audit.example?");
        assertThrows(BaseException.class, () -> service.loadOrders(website, buildRequest(null), "", SyncMode.AUTO));
        verifyNoInteractions(restTemplate, tokenService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void shouldResolveMetafieldOwnerWithinWebsiteCompany(int matchType) {
        boolean telephoneMatches = matchType == 1;
        JSONObject order = buildShopifyOrder();
        order.getJSONArray("line_items").getJSONObject(0).set("product_id", "9001");
        JSONArray orders = new JSONArray();
        orders.add(order);
        when(tokenService.getAccessToken(any())).thenReturn("old-token");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(invocation -> {
                    URI uri = invocation.getArgument(0);
                    return uri.getPath().endsWith("metafields.json")
                            ? ResponseEntity.ok("{\"metafields\":[{\"key\":\"owner_telephone\",\"value\":\"123\"},{\"key\":\"owner_name\",\"value\":\"同名用户\"}]}")
                            : ordersResponse(orders, null);
                });
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
        order.set("current_total_price_set", moneySet("99.99", "89.99"));
        order.set("current_total_discounts_set", moneySet("10.00", "9.00"));
        order.set("current_total_tax_set", moneySet("5.00", "4.50"));

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

    private JSONObject buildBareOrder(String id, String createdAt) {
        JSONObject order = new JSONObject();
        order.set("id", id);
        order.set("created_at", createdAt);
        return order;
    }

    private ResponseEntity<String> ordersResponse(JSONArray orders, String linkHeader) {
        JSONObject body = new JSONObject();
        body.set("orders", orders);
        HttpHeaders headers = new HttpHeaders();
        if (linkHeader != null) {
            headers.add("link", linkHeader);
        }
        return new ResponseEntity<>(body.toString(), headers, HttpStatus.OK);
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

    // ==================== 请求参数测试 ====================

    @Nested
    @DisplayName("loadOrders 请求参数")
    class LoadOrdersRequest {

        @Test
        @DisplayName("自动同步且有游标：携带 status=any、since_id 和 UTC 时间，不带 order")
        void shouldUseSinceIdWhenAutoSyncWithCursor() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
            ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(uriCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(), null));

            service.loadOrders(buildWebsiteDto("5500000000000"), buildRequest(LocalDateTime.of(2026, 6, 1, 8, 0)), "", SyncMode.AUTO);

            String uri = uriCaptor.getValue().toString();
            assertTrue(uri.startsWith("https://test-shop.myshopify.com/admin/api/2026-07/orders.json"));
            assertTrue(uri.contains("status=any"));
            assertTrue(uri.contains("since_id=5500000000000"));
            assertTrue(uri.contains("created_at_min=2026-06-01T00:00:00Z"));
            assertTrue(uri.contains("limit=100"));
            assertFalse(uri.contains("order="));
            assertEquals("old-token", entityCaptor.getValue().getHeaders().getFirst("X-Shopify-Access-Token"));
            verify(websiteStore).updateLastSyncInfo(eq(1L), any());
        }

        @Test
        @DisplayName("手动同步：按 created_at 升序，不带 since_id，不更新自动同步游标")
        void shouldOrderByCreatedAtWhenManualSync() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
            when(restTemplate.exchange(uriCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(), null));

            service.loadOrders(buildWebsiteDto("5500000000000"), buildRequest(null), "", SyncMode.MANUAL);

            String uri = uriCaptor.getValue().toString();
            assertTrue(uri.contains("order=created_at%20asc"));
            assertFalse(uri.contains("since_id"));
            verify(websiteStore, never()).updateLastSyncInfo(any(), any());
        }

        @Test
        @DisplayName("翻页：只携带 page_info 和 limit，并解析下一页")
        void shouldOnlySendPageInfoWhenPaging() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
            when(restTemplate.exchange(uriCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ordersResponse(new JSONArray(),
                            "<https://test-shop.myshopify.com/admin/api/2026-07/orders.json?limit=100&page_info=prev1>; rel=\"previous\", "
                                    + "<https://test-shop.myshopify.com/admin/api/2026-07/orders.json?limit=100&page_info=next2>; rel=\"next\""));

            ShoplineOrderLoadResult result = service.loadOrders(buildWebsiteDto(null),
                    buildRequest(LocalDateTime.of(2026, 6, 1, 8, 0)), "page1", SyncMode.MANUAL);

            assertEquals("page_info=page1&limit=100", uriCaptor.getValue().getQuery());
            assertEquals("next2", result.getNextPageInfo());
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
            ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class)))
                    .thenThrow(unauthorized())
                    .thenReturn(ordersResponse(new JSONArray(), null));

            assertDoesNotThrow(() -> service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO));

            assertEquals("new-token", entityCaptor.getAllValues().get(1).getHeaders().getFirst("X-Shopify-Access-Token"));
            verify(websiteStore, never()).markWebsiteAuthError(any(), any());
        }

        @Test
        @DisplayName("刷新后仍 401 时应标记商城授权异常")
        void shouldMarkAuthErrorWhenStillUnauthorized() {
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(tokenService.forceRefresh(1L, "old-token")).thenReturn("new-token");
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
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

        @Test
        void shouldUseCurrentQuantityAndExcludeRemovedProducts() {
            JSONObject order = buildShopifyOrder();
            JSONArray items = order.getJSONArray("line_items");
            items.getJSONObject(0).set("quantity", 3).set("current_quantity", 1);
            items.add(new JSONObject().set("quantity", 2).set("current_quantity", 0)
                    .set("sku", "REMOVED").set("product_id", "removed-product"));

            EditTemporaryOrderRequest request = loadAndCapture(buildWebsiteDto(null), order);

            assertEquals(1, request.getItemInfos().size());
            assertEquals(1, request.getItemInfos().get(0).getQuantity());
            verify(productSKUService).listBySkuCodes(eq(List.of("SKU-001")), anyLong());
            verify(restTemplate, times(1)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            verifyNoInteractions(systemUserRepository);
        }

        @Test
        void shouldAllowAllItemsToBeRemoved() {
            JSONObject order = buildShopifyOrder();
            order.getJSONArray("line_items").getJSONObject(0).set("current_quantity", 0);
            assertTrue(loadAndCapture(buildWebsiteDto(null), order).getItemInfos().isEmpty());
            verifyNoInteractions(productSKUService);
        }

        private EditTemporaryOrderRequest loadAndCapture(ThirdPartyWebsiteDto website, JSONObject order) {
            JSONArray orders = new JSONArray();
            orders.add(order);
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ordersResponse(orders, null));
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), anyBoolean())).thenReturn(true);

            service.loadOrders(website, buildRequest(null), "", SyncMode.MANUAL);
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
        @DisplayName("同一页内相同商品的 metafields 只请求一次，并用于补全 SKU 与品名")
        void shouldFetchMetafieldsOncePerProduct() {
            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());
            JSONObject order1 = buildShopifyOrder();
            order1.getJSONArray("line_items").getJSONObject(0).set("product_id", "9001");
            JSONObject order2 = buildShopifyOrder();
            order2.set("id", "5500000000002");
            order2.getJSONArray("line_items").getJSONObject(0).set("product_id", "9001");
            JSONArray orders = new JSONArray();
            orders.add(order1);
            orders.add(order2);

            String metafields = "{\"metafields\":[{\"key\":\"sku_code_high\",\"value\":\"SKU-HIGH\"},{\"key\":\"cn_product_name\",\"value\":\"测试商品\"}]}";
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenAnswer(invocation -> {
                        URI uri = invocation.getArgument(0);
                        if (uri.getPath().endsWith("/products/9001/metafields.json")) {
                            return new ResponseEntity<>(metafields, HttpStatus.OK);
                        }
                        return ordersResponse(orders, null);
                    });
            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), anyBoolean())).thenReturn(true);

            service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.MANUAL);

            // 1 次订单列表 + 1 次 metafields
            verify(restTemplate, times(2)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
            assertEquals(2, captor.getAllValues().size());
            assertEquals("SKU-HIGH", captor.getAllValues().get(1).getItemInfos().get(0).getSkuCode());
            assertEquals("测试商品", captor.getAllValues().get(1).getItemInfos().get(0).getMerchandise());
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
                orders.add(buildBareOrder(id, "2026-01-01T10:00:00+08:00"));
            }
            when(tokenService.getAccessToken(any())).thenReturn("old-token");
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ordersResponse(orders, "<https://test-shop.myshopify.com/orders.json?page_info=next>; rel=\"next\""));
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
            orders.add(buildBareOrder("10", "2026-01-01T10:00:00+08:00"));
            orders.add(buildBareOrder("20", "2026-01-01T11:00:00+08:00"));
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
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ordersResponse(orders, null));
            return service.loadOrders(buildWebsiteDto(null), buildRequest(null), "", SyncMode.AUTO);
        }

        @Test
        @DisplayName("已存在的订单跳过，游标仍推进")
        void shouldSkipExistingOrders() {
            JSONArray orders = new JSONArray();
            orders.add(buildBareOrder("10", "2026-01-01T10:00:00+08:00"));
            orders.add(buildBareOrder("30", "2026-01-01T12:00:00+08:00"));
            when(temporaryOrderService.findByOriginOrderId(anyString()))
                    .thenReturn(Optional.of(TemporaryOrder.builder().build()));

            ShoplineOrderLoadResult result = loadAuto(orders);

            assertEquals("30", result.getCursorOrderId());
            assertEquals(2, result.getSkippedCount());
            assertEquals(0, result.getCreatedCount());
            verify(temporaryOrderService, never()).synchronizeOrderFromExternalSystem(any(), anyBoolean());
        }

        @Test
        @DisplayName("中间出现失败单：立即终止本页，游标停在失败单前")
        void shouldStopAtFailure() {
            JSONArray orders = new JSONArray();
            orders.add(buildBareOrder("10", "2026-01-01T10:00:00+08:00"));
            orders.add(buildBareOrder("20", "2026-01-01T11:00:00+08:00"));
            orders.add(buildBareOrder("30", "2026-01-01T12:00:00+08:00"));
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

    // ==================== 授权验证测试 ====================

    @Nested
    @DisplayName("verifyAndUpdateAuthStatus")
    class VerifyAuthStatus {

        private ThirdPartyWebsite buildWebsite() {
            return ThirdPartyWebsite.builder()
                    .handle("test-shop")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .websiteType(WebsiteTypeEnum.SHOPIFY)
                    .build();
        }

        @Test
        @DisplayName("换取 token 成功且接口可用时应设置 AUTHED 并写入 token")
        void shouldSetAuthedWhenCredentialsValid() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            when(tokenService.fetchToken("test-shop", "client-id", "client-secret"))
                    .thenReturn(new ShopifyTokenService.ShopifyToken("new-token", expiresAt));
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"count\": 5}", HttpStatus.OK));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, website.getAuthStatus());
            assertEquals("new-token", website.getToken());
            assertEquals(expiresAt, website.getTokenExpiresAt());
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
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));
            ThirdPartyWebsite website = buildWebsite();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertTrue(website.getAuthMessage().contains("read_orders"));
        }
    }
}
