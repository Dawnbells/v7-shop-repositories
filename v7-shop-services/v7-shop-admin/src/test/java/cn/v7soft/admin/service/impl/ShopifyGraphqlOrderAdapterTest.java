package cn.v7soft.admin.service.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ICurrencyService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.enums.BrowserPlatform;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopifyGraphqlOrderAdapterTest {

    /**
     * 转换器（ShopifyOrderSyncService.convertShopifyOrderToTemporary 及其下游）读取的全部 REST 键。
     * client_details.user_agent 在 GraphQL 中没有对应字段，单独断言为缺省。
     */
    private static final List<String> CONVERTER_ORDER_KEYS = List.of(
            "id", "name", "created_at", "updated_at", "cancelled_at", "landing_site", "note", "email", "contact_email",
            "financial_status", "fulfillment_status", "currency", "presentment_currency", "customer_locale", "browser_ip",
            "total_price_set", "total_price", "total_discounts_set", "total_discounts", "total_tax_set", "total_tax",
            "shipping_lines", "note_attributes", "customer", "shipping_address", "billing_address", "line_items");
    private static final List<String> CONVERTER_ADDRESS_KEYS = List.of(
            "first_name", "last_name", "phone", "province", "city", "zip", "address1", "address2", "country_code");
    private static final List<String> CONVERTER_CUSTOMER_KEYS = List.of("first_name", "last_name", "phone", "email");
    private static final List<String> CONVERTER_LINE_ITEM_KEYS = List.of(
            "product_id", "title", "variant_title", "price_set", "price", "quantity", "current_quantity", "sku");

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
        ShopifyGraphqlClient graphqlClient = new ShopifyGraphqlClient(restTemplate, tokenService, websiteStore, appProperties) {
            @Override
            protected void sleep(long millis) {
            }
        };
        service = new ShopifyOrderSyncService(graphqlClient, restTemplate, appProperties, tokenService, websiteStore,
                currencyService, languageService, countryService, addressService, temporaryOrderService,
                productSKUService, systemUserRepository);
    }

    private static JSONObject resource(String name) {
        return JSONUtil.parseObj(ResourceUtil.readStr("shopify/" + name, StandardCharsets.UTF_8));
    }

    private ThirdPartyWebsiteDto website(CurrencyMode currencyMode) {
        return ThirdPartyWebsiteDto.builder()
                .id("1")
                .nickName("TestShop")
                .handle("test-shop")
                .token("token")
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .currencyMode(currencyMode)
                .owner(SystemUserDto.builder().id("1").companyId(100L).name("张三")
                        .departmentId(10L).departmentName("COD一部").build())
                .build();
    }

    @Test
    @DisplayName("适配器输出包含转换器读取的全部键")
    void shouldCoverEveryKeyReadByConverter() {
        JSONObject node = resource("order-graphql.json");
        // 样例为未取消订单，可空键需要给值才能断言存在
        node.set("cancelledAt", "2026-06-02T00:00:00Z");
        node.getJSONObject("billingAddress").set("address2", "Suite 1");
        JSONObject order = ShopifyGraphqlOrderAdapter.toRestOrder(node, new HashMap<>());

        CONVERTER_ORDER_KEYS.forEach(key -> assertTrue(order.containsKey(key), "缺少订单键 " + key));
        for (String addressKey : List.of("shipping_address", "billing_address")) {
            JSONObject address = order.getJSONObject(addressKey);
            CONVERTER_ADDRESS_KEYS.forEach(key -> assertTrue(address.containsKey(key), "缺少 " + addressKey + "." + key));
        }
        JSONObject customer = order.getJSONObject("customer");
        CONVERTER_CUSTOMER_KEYS.forEach(key -> assertTrue(customer.containsKey(key), "缺少 customer." + key));
        JSONObject lineItem = order.getJSONArray("line_items").getJSONObject(0);
        CONVERTER_LINE_ITEM_KEYS.forEach(key -> assertTrue(lineItem.containsKey(key), "缺少 line_items[]." + key));
        assertTrue(order.getJSONArray("shipping_lines").getJSONObject(0).containsKey("price_set"));
        JSONObject attribute = order.getJSONArray("note_attributes").getJSONObject(0);
        assertEquals("IP Address", attribute.getStr("name"));
        assertEquals("179.255.146.65", attribute.getStr("value"));
        // GraphQL 不提供浏览器 UA
        assertNull(order.getJSONObject("client_details"));
    }

    @Test
    @DisplayName("适配器按映射表输出 REST 形状，与 REST 样例逐键一致")
    void shouldMapToRestShape() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        JSONObject adapted = ShopifyGraphqlOrderAdapter.toRestOrder(resource("order-graphql.json"), cache);
        JSONObject rest = resource("order-rest.json");
        // REST 中 GraphQL 无法提供的键
        rest.remove("client_details");

        assertEquals(normalize(rest), normalize(adapted));
        assertEquals("5500000000001", adapted.getStr("id"));
        assertEquals(Map.of("cn_product_name", "测试商品", "waybill_product_name", "运单品名",
                "sku_code_high", "SKU-HIGH", "owner_name", "归属人"), cache.get("9001"));
        assertEquals(Map.of("sku_code", "SKU-GIFT"), cache.get("9002"));
    }

    @ParameterizedTest
    @EnumSource(CurrencyMode.class)
    @DisplayName("同一订单的 GraphQL 样例与 REST 样例经转换器得到的审单请求逐字段相等")
    void shouldProduceSameTemporaryOrderRequestAsRestPayload(CurrencyMode currencyMode) {
        JSONObject graphqlNode = resource("order-graphql.json");
        JSONArray productNodes = new JSONArray();
        JSONArray lineItems = graphqlNode.getJSONObject("lineItems").getJSONArray("nodes");
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject product = lineItems.getJSONObject(i).getJSONObject("product");
            productNodes.add(new JSONObject().set("id", "gid://shopify/Product/" + product.getStr("legacyResourceId"))
                    .set("legacyResourceId", product.getStr("legacyResourceId"))
                    .set("metafields", product.getJSONObject("metafields")));
        }
        when(tokenService.getAccessToken(any())).thenReturn("token");
        ArgumentCaptor<HttpEntity<Map<String, Object>>> requests = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.postForEntity(anyString(), requests.capture(), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> entity = invocation.getArgument(1);
            String query = (String) entity.getBody().get("query");
            JSONObject data = query.contains("nodes(ids:")
                    ? new JSONObject().set("nodes", productNodes)
                    : new JSONObject().set("orders", new JSONObject()
                    .set("pageInfo", new JSONObject().set("hasNextPage", false))
                    .set("nodes", new JSONArray().set(graphqlNode)));
            return ResponseEntity.ok(new JSONObject().set("data", data).toString());
        });
        ArgumentCaptor<EditTemporaryOrderRequest> captor = ArgumentCaptor.forClass(EditTemporaryOrderRequest.class);
        when(temporaryOrderService.synchronizeOrderFromExternalSystem(captor.capture(), eq(true))).thenReturn(true);

        service.loadOrders(website(currencyMode), new SyncThirdPartyOrdersRequest(), "", SyncMode.MANUAL);
        // 订单查询内联返回元字段，不再单独查询商品
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        service.importWebhookOrder(website(currencyMode), resource("order-rest.json"), true);
        // webhook 路径缓存未命中，用 nodes 一次批量查询两个商品
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        Map<String, Object> variables = (Map<String, Object>) requests.getAllValues().get(1).getBody().get("variables");
        assertEquals(List.of("gid://shopify/Product/9001", "gid://shopify/Product/9002"), variables.get("ids"));

        EditTemporaryOrderRequest fromGraphql = captor.getAllValues().get(0);
        EditTemporaryOrderRequest fromRest = captor.getAllValues().get(1);

        // 支付时间取当前时间；UA 只在 REST 的 client_details 里有
        assertEquals("", fromGraphql.getRiskInfo().getUa());
        assertEquals(BrowserPlatform.UNKNOWN, fromGraphql.getRiskInfo().getBrowserPlatform());
        assertTrue(fromRest.getRiskInfo().getUa().startsWith("Mozilla/5.0"));
        fromRest.getRiskInfo().setUa(fromGraphql.getRiskInfo().getUa());
        fromRest.getRiskInfo().setBrowserPlatform(fromGraphql.getRiskInfo().getBrowserPlatform());
        fromRest.getPaymentInfo().setPaymentTime(null);
        fromGraphql.getPaymentInfo().setPaymentTime(null);

        assertEquals(toJson(fromRest), toJson(fromGraphql));
        // 关键字段抽查：元字段与 legacy id 生效
        assertEquals("5500000000001", fromGraphql.getOriginOrderId());
        assertEquals("SKU-HIGH", fromGraphql.getItemInfos().get(0).getSkuCode());
        assertEquals("测试商品", fromGraphql.getItemInfos().get(0).getMerchandise());
        assertEquals("运单品名", fromGraphql.getItemInfos().get(0).getWaybillProductName());
        assertEquals("SKU-GIFT", fromGraphql.getItemInfos().get(1).getSkuCode());
        assertEquals("192.168.1.1", fromGraphql.getRiskInfo().getRemoteIp());
        assertEquals("jane@example.com", fromGraphql.getDeliveryInfo().getEmail());
        assertEquals("https://test-shop.myshopify.com/products/test", fromGraphql.getFromUrl());
    }

    @Test
    @DisplayName("状态枚举映射为 REST 取值")
    void shouldMapStatuses() {
        assertEquals("fulfilled", ShopifyGraphqlOrderAdapter.fulfillmentStatus("FULFILLED"));
        assertEquals("partial", ShopifyGraphqlOrderAdapter.fulfillmentStatus("PARTIALLY_FULFILLED"));
        assertNull(ShopifyGraphqlOrderAdapter.fulfillmentStatus("UNFULFILLED"));
        assertEquals("restocked", ShopifyGraphqlOrderAdapter.fulfillmentStatus("RESTOCKED"));
        assertNull(ShopifyGraphqlOrderAdapter.fulfillmentStatus(null));

        JSONObject node = new JSONObject().set("id", "gid://shopify/Order/42").set("displayFinancialStatus", "PARTIALLY_REFUNDED")
                .set("cancelReason", "CUSTOMER");
        JSONObject order = ShopifyGraphqlOrderAdapter.toRestOrder(node, null);
        assertEquals("42", order.getStr("id"));
        assertEquals("partially_refunded", order.getStr("financial_status"));
        assertEquals("customer", order.getStr("cancel_reason"));
        assertNull(order.getStr("fulfillment_status"));
    }

    @Test
    @DisplayName("兼容 edges.node 写法；未查询元字段的商品不写缓存")
    void shouldSupportEdgesAndSkipMissingMetafields() {
        JSONObject lineItem = new JSONObject().set("id", "gid://shopify/LineItem/7").set("quantity", 1)
                .set("currentQuantity", 1).set("product", new JSONObject().set("legacyResourceId", "9003"));
        JSONObject node = new JSONObject().set("legacyResourceId", "43")
                .set("lineItems", new JSONObject().set("edges", new JSONArray().set(new JSONObject().set("node", lineItem))));
        Map<String, Map<String, String>> cache = new HashMap<>();

        JSONObject order = ShopifyGraphqlOrderAdapter.toRestOrder(node, cache);

        JSONObject restLineItem = order.getJSONArray("line_items").getJSONObject(0);
        assertEquals("7", restLineItem.getStr("id"));
        assertEquals("9003", restLineItem.getStr("product_id"));
        assertEquals(1, restLineItem.getInt("current_quantity"));
        assertTrue(cache.isEmpty());
    }

    @Test
    void shouldParseLegacyIds() {
        assertEquals("123", ShopifyGraphqlOrderAdapter.legacyId("gid://shopify/LineItem/123"));
        assertEquals("123", ShopifyGraphqlOrderAdapter.legacyId("gid://shopify/LineItem/123?foo=bar"));
        assertEquals("123", ShopifyGraphqlOrderAdapter.legacyId("123"));
        assertNull(ShopifyGraphqlOrderAdapter.legacyId(null));
        assertEquals("gid://shopify/Product/9", ShopifyGraphqlOrderAdapter.productGid("9"));
    }

    /**
     * 统一为字符串比较（REST 的数字 id 与 GraphQL 的字符串 id 等价；带时区的时间统一为 UTC 瞬时），去掉空值
     */
    private static Object normalize(Object value) {
        if (value instanceof JSONObject object) {
            Map<String, Object> result = new java.util.TreeMap<>();
            object.forEach((key, child) -> {
                if (child != null && !JSONUtil.isNull(child)) {
                    result.put(key, normalize(child));
                }
            });
            return result;
        }
        if (value instanceof JSONArray array) {
            return array.stream().map(ShopifyGraphqlOrderAdapterTest::normalize).toList();
        }
        String text = String.valueOf(value);
        try {
            return java.time.OffsetDateTime.parse(text).toInstant().toString();
        } catch (java.time.format.DateTimeParseException e) {
            return text;
        }
    }

    private static String toJson(Object bean) {
        return JSONUtil.toJsonPrettyStr(JSONUtil.parseObj(bean, JSONConfig.create().setIgnoreNullValue(false)));
    }
}
