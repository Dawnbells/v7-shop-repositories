package cn.v7soft.admin.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderContextInfoRequest;
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThirdPartyWebsiteServiceTest {

    @Mock private ThirdPartyWebsiteRepository repository;
    @Mock private RestTemplate restTemplate;
    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private ITaskExecutorService taskExecutorService;
    @Mock private ICurrencyService currencyService;
    @Mock private ILanguageService languageService;
    @Mock private ICountryService countryService;
    @Mock private ITemporaryOrderService temporaryOrderService;

    @InjectMocks
    private ThirdPartyWebsiteService service;

    private SystemUserDto buildOwner() {
        return SystemUserDto.builder()
                .id("1")
                .companyId(100L)
                .name("张三")
                .departmentId(10L)
                .departmentName("COD一部")
                .build();
    }

    private ThirdPartyWebsiteDto buildWebsiteDto() {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .nickName("TestShop")
                .handle("test-shop")
                .token("valid-token")
                .appKey("app-key")
                .appSecret("app-secret")
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .authType(ThirdPartyAuthTypeEnum.OAUTH2)
                .websiteType(WebsiteTypeEnum.SHOPLINE)
                .owner(buildOwner())
                .build();
    }

    private JSONObject buildShoplineOrder() {
        JSONObject order = new JSONObject();
        order.set("id", "SL-ORDER-001");
        order.set("created_at", "2025-06-01T10:30:00+08:00");
        order.set("landing_site", "https://test-shop.myshopline.com/product");
        order.set("currency", "USD");
        order.set("customer_locale", "en_US");
        order.set("current_total_price", "99.99");
        order.set("current_total_discounts", "10.00");
        order.set("current_total_tax", "5.00");
        order.set("financial_status", "paid");
        order.set("buyer_note", "请尽快发货");

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

        JSONObject customer = new JSONObject();
        customer.set("email", "john@example.com");
        customer.set("first_name", "John");
        customer.set("last_name", "Doe");
        order.set("customer", customer);

        JSONObject clientDetails = new JSONObject();
        clientDetails.set("browser_ip", "192.168.1.1");
        clientDetails.set("user_agent", "Mozilla/5.0");
        order.set("client_details", clientDetails);

        JSONArray shippingLines = new JSONArray();
        JSONObject shippingLine = new JSONObject();
        shippingLine.set("price", "5.99");
        shippingLines.add(shippingLine);
        order.set("shipping_lines", shippingLines);

        JSONArray lineItems = new JSONArray();
        JSONObject item = new JSONObject();
        item.set("title", "Test Product");
        item.set("attribute", "Color: Red");
        item.set("price", "49.99");
        item.set("quantity", "2");
        item.set("sku", "SKU-001");
        item.set("variant_id", "VAR-001");
        lineItems.add(item);
        order.set("line_items", lineItems);

        return order;
    }

    // ==================== 验证逻辑测试 ====================

    @Nested
    @DisplayName("verifyAndUpdateAuthStatus")
    class VerifyAuthStatus {

        @Test
        @DisplayName("Token有效时应设置AUTHED")
        void shouldSetAuthedWhenTokenValid() {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                    .handle("test-shop")
                    .token("valid-token")
                    .websiteType(WebsiteTypeEnum.SHOPLINE)
                    .build();

            ResponseEntity<String> successResponse = new ResponseEntity<>(
                    "{\"count\": 5}", HttpStatus.OK);
            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(successResponse);

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, website.getAuthStatus());
            assertNull(website.getAuthMessage());
        }

        @Test
        @DisplayName("Token无效(401)时应设置ERROR")
        void shouldSetErrorWhenUnauthorized() {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                    .handle("test-shop")
                    .token("bad-token")
                    .websiteType(WebsiteTypeEnum.SHOPLINE)
                    .build();

            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertEquals("Token无效或已过期", website.getAuthMessage());
        }

        @Test
        @DisplayName("网络不可达时应设置ERROR并提示检查Handle")
        void shouldSetErrorWhenNetworkFails() {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                    .handle("bad-handle")
                    .token("token")
                    .websiteType(WebsiteTypeEnum.SHOPLINE)
                    .build();

            when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.ERROR, website.getAuthStatus());
            assertTrue(website.getAuthMessage().contains("无法连接到Shopline"));
        }

        @Test
        @DisplayName("非SHOPLINE类型应跳过验证")
        void shouldSkipNonShoplineWebsite() {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                    .handle("test")
                    .token("token")
                    .websiteType(WebsiteTypeEnum.V7_SHOP)
                    .authStatus(ThirdPartyAuthStatusEnum.INIT)
                    .build();

            service.verifyAndUpdateAuthStatus(website);

            assertEquals(ThirdPartyAuthStatusEnum.INIT, website.getAuthStatus());
            verifyNoInteractions(restTemplate);
        }
    }

    // ==================== 订单转换测试 ====================

    @Nested
    @DisplayName("convertShoplineOrderToTemporary")
    class ConvertOrder {

        @Test
        @DisplayName("完整订单应正确映射所有字段写入临时表")
        void shouldConvertFullOrderToTemporary() {
            ThirdPartyWebsiteDto websiteDto = buildWebsiteDto();
            JSONObject order = buildShoplineOrder();

            Currency usd = Currency.builder().code("USD").symbol("$").name("美元").build();
            setId(usd, 1L);
            when(currencyService.getByCode("USD")).thenReturn(Optional.of(usd));

            Language en = Language.builder().code("en").name("English").build();
            setId(en, 1L);
            when(languageService.getByCode("en")).thenReturn(Optional.of(en));

            Country us = Country.builder().code("US").name("United States").build();
            setId(us, 1L);
            when(countryService.getByCode("US")).thenReturn(Optional.of(us));

            JSONArray orders = new JSONArray();
            orders.add(order);

            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            doNothing().when(temporaryOrderService).synchronizeOrderFromExternalSystem(captor.capture());

            // 通过 convertAndSaveOrders 间接调用 convertShoplineOrderToTemporary（private 方法）
            // 使用反射调用
            invokeConvertAndSaveOrders(websiteDto, orders);

            EditTemporaryOrderRequest req = captor.getValue();

            assertEquals(100L, req.getCompanyId());
            assertEquals("TestShop", req.getFrom());
            assertEquals(WebsiteTypeEnum.SHOPLINE, req.getPlatform());
            assertEquals("SL-ORDER-001", req.getOriginOrderId());
            assertNotNull(req.getOrderTime());

            // deliveryInfo
            assertNotNull(req.getDeliveryInfo());
            assertEquals("John", req.getDeliveryInfo().getFirstName());
            assertEquals("Doe", req.getDeliveryInfo().getLastName());
            assertEquals("+1234567890", req.getDeliveryInfo().getPhone());
            assertEquals("California", req.getDeliveryInfo().getProvince());
            assertEquals("Los Angeles", req.getDeliveryInfo().getCity());
            assertEquals("123 Main St", req.getDeliveryInfo().getAddress());
            assertEquals("90001", req.getDeliveryInfo().getPostalCode());
            assertEquals("john@example.com", req.getDeliveryInfo().getEmail());
            assertEquals("请尽快发货", req.getDeliveryInfo().getRemark());

            // financialInfo
            assertNotNull(req.getFinancialInfo());
            assertEquals(new BigDecimal("99.99"), req.getFinancialInfo().getTotalAmount());
            assertEquals(new BigDecimal("5.99"), req.getFinancialInfo().getShippingFee());
            assertEquals(new BigDecimal("10.00"), req.getFinancialInfo().getDiscountAmount());
            assertEquals(new BigDecimal("5.00"), req.getFinancialInfo().getTaxAmount());

            // paymentInfo
            assertNotNull(req.getPaymentInfo());
            assertEquals(PaymentMethod.COD, req.getPaymentInfo().getPaymentMethod());
            assertEquals(PaymentStatus.PAID, req.getPaymentInfo().getPaymentStatus());

            // contextInfo
            TemporaryOrderContextInfoRequest ctx = req.getContextInfo();
            assertNotNull(ctx);
            assertEquals(1L, ctx.getSalesUid());
            assertEquals("张三", ctx.getSalesPerson());
            assertEquals("COD一部", ctx.getDepartment());
            assertEquals(1L, ctx.getWebsiteId());
            assertEquals("TestShop", ctx.getWebsiteName());
            assertEquals("USD", ctx.getCurrencyCode());
            assertEquals("$", ctx.getCurrencySymbol());
            assertEquals("en", ctx.getLanguageCode());
            assertEquals("US", ctx.getCountryCode());

            // riskInfo
            assertNotNull(req.getRiskInfo());
            assertEquals("192.168.1.1", req.getRiskInfo().getRemoteIp());
            assertEquals("Mozilla/5.0", req.getRiskInfo().getUa());

            // itemInfos
            assertNotNull(req.getItemInfos());
            assertEquals(1, req.getItemInfos().size());
            assertEquals("Test Product", req.getItemInfos().get(0).getTitle());
            assertEquals(new BigDecimal("49.99"), req.getItemInfos().get(0).getSellPrice());
            assertEquals(2, req.getItemInfos().get(0).getQuantity());
            assertEquals("SKU-001", req.getItemInfos().get(0).getSkuName());
        }

        @Test
        @DisplayName("重复订单应跳过不抛异常")
        void shouldSkipDuplicateOrder() {
            ThirdPartyWebsiteDto websiteDto = buildWebsiteDto();
            JSONObject order = buildShoplineOrder();
            JSONArray orders = new JSONArray();
            orders.add(order);

            doThrow(new RuntimeException("已存在相同的原始订单ID：SL-ORDER-001"))
                    .when(temporaryOrderService).synchronizeOrderFromExternalSystem(any());

            assertDoesNotThrow(() -> invokeConvertAndSaveOrders(websiteDto, orders));
        }

        @Test
        @DisplayName("没有shipping_address时应从customer获取信息")
        void shouldFallbackToCustomerWhenNoShippingAddress() {
            ThirdPartyWebsiteDto websiteDto = buildWebsiteDto();
            JSONObject order = buildShoplineOrder();
            order.remove("shipping_address");
            order.remove("customer_locale");
            JSONArray orders = new JSONArray();
            orders.add(order);

            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            doNothing().when(temporaryOrderService).synchronizeOrderFromExternalSystem(captor.capture());

            invokeConvertAndSaveOrders(websiteDto, orders);

            EditTemporaryOrderRequest req = captor.getValue();
            assertEquals("John", req.getDeliveryInfo().getFirstName());
            assertEquals("Doe", req.getDeliveryInfo().getLastName());
            assertEquals("john@example.com", req.getDeliveryInfo().getEmail());
        }

        @Test
        @DisplayName("没有line_items时itemInfos应为空列表")
        void shouldReturnEmptyItemsWhenNoLineItems() {
            ThirdPartyWebsiteDto websiteDto = buildWebsiteDto();
            JSONObject order = buildShoplineOrder();
            order.remove("line_items");
            order.remove("customer_locale");
            JSONArray orders = new JSONArray();
            orders.add(order);

            lenient().when(currencyService.getByCode(anyString())).thenReturn(Optional.empty());
            lenient().when(countryService.getByCode(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
            doNothing().when(temporaryOrderService).synchronizeOrderFromExternalSystem(captor.capture());

            invokeConvertAndSaveOrders(websiteDto, orders);

            assertTrue(captor.getValue().getItemInfos().isEmpty());
        }
    }

    // ==================== link header 解析测试 ====================

    @Nested
    @DisplayName("extractNextPageInfo")
    class ExtractPageInfo {

        @Test
        @DisplayName("有next link时应正确提取page_info")
        void shouldExtractPageInfoFromLinkHeader() throws Exception {
            HttpHeaders headers = new HttpHeaders();
            headers.add("link", "<https://test.myshopline.com/orders.json?page_info=abc123&limit=100>; rel=\"next\"");

            var method = ThirdPartyWebsiteService.class.getDeclaredMethod("extractNextPageInfo", HttpHeaders.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, headers);

            assertEquals("abc123", result);
        }

        @Test
        @DisplayName("没有link header时应返回null")
        void shouldReturnNullWhenNoLinkHeader() throws Exception {
            HttpHeaders headers = new HttpHeaders();

            var method = ThirdPartyWebsiteService.class.getDeclaredMethod("extractNextPageInfo", HttpHeaders.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, headers);

            assertNull(result);
        }

        @Test
        @DisplayName("只有previous没有next时应返回null")
        void shouldReturnNullWhenOnlyPrevious() throws Exception {
            HttpHeaders headers = new HttpHeaders();
            headers.add("link", "<https://test.myshopline.com/orders.json?page_info=prev123>; rel=\"previous\"");

            var method = ThirdPartyWebsiteService.class.getDeclaredMethod("extractNextPageInfo", HttpHeaders.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, headers);

            assertNull(result);
        }
    }

    // ==================== 时间解析测试 ====================

    @Nested
    @DisplayName("parseShoplineDateTime")
    class ParseDateTime {

        @Test
        @DisplayName("ISO8601带时区应正确解析")
        void shouldParseIso8601WithTimezone() throws Exception {
            var method = ThirdPartyWebsiteService.class.getDeclaredMethod("parseShoplineDateTime", String.class);
            method.setAccessible(true);
            var result = method.invoke(service, "2025-06-01T10:30:00+08:00");

            assertNotNull(result);
        }

        @Test
        @DisplayName("空字符串应返回当前时间")
        void shouldReturnNowForBlankString() throws Exception {
            var method = ThirdPartyWebsiteService.class.getDeclaredMethod("parseShoplineDateTime", String.class);
            method.setAccessible(true);
            var result = method.invoke(service, "");

            assertNotNull(result);
        }
    }

    // ==================== 查询同步商城测试 ====================

    @Test
    @DisplayName("findSyncEnabledWebsites应查询AUTHED且启用的商城")
    void shouldFindOnlyAuthedAndEnabledWebsites() {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                .nickName("TestShop")
                .handle("test")
                .syncEnabled(true)
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .build();
        when(repository.findBySyncEnabledTrueAndAuthStatus(ThirdPartyAuthStatusEnum.AUTHED))
                .thenReturn(List.of(website));

        List<ThirdPartyWebsite> result = service.findSyncEnabledWebsites();

        assertEquals(1, result.size());
        assertEquals("TestShop", result.get(0).getNickName());
    }

    // ==================== 辅助方法 ====================

    private void invokeConvertAndSaveOrders(ThirdPartyWebsiteDto website, JSONArray orders) {
        try {
            var method = ThirdPartyWebsiteService.class.getDeclaredMethod(
                    "convertAndSaveOrders", ThirdPartyWebsiteDto.class, JSONArray.class);
            method.setAccessible(true);
            method.invoke(service, website, orders);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
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
}
