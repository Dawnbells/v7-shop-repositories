package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.EditTemporaryOrderRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderContextInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderDeliveryInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderFinancialInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderItemInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderPaymentInfoRequest;
import cn.v7soft.admin.controller.req.TemporaryOrderRiskRecordInfoRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ICurrencyService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ShopifyCancelCheckResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.TemporaryOrder;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.BrowserPlatform;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.enums.PaymentStatus;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Shopify 订单同步（Admin GraphQL API）。
 * <p>
 * GraphQL 订单节点先经 {@link ShopifyGraphqlOrderAdapter} 转成 REST 形状（与 webhook payload 同形），
 * 再由同一套转换器写入审单表。与 Shopline 的实现（ThirdPartyWebsiteService）相互独立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService implements IShopifyOrderSyncService {
    /*
     * 单个 GraphQL 查询的请求成本上限为 1000 点（按 first 估算，嵌套连接相乘：对象 1 点，连接 2 + first × 节点成本）。
     * 订单页：每个行项目 ≈ 14 点（自身 1 + 单价 MoneyBag 3 + variant 1 + product 1 + 元字段 2+6），
     * 每单 ≈ 108 点（订单 1 + 5 个 MoneyBag 15 + 访问摘要 3 + 附加属性 1 + 地址 2 + 运费行 2+3×4 + 行项目 2+5×14），
     * 8 单/页 ≈ 870 点。行项目超过 5 个的订单单独补查。
     * 不查 customer：收货人取自收货/账单地址，邮箱电话取订单自身字段，因此无需 read_customers 权限。
     */
    static final int ORDER_PAGE_SIZE = 8;
    static final int LINE_ITEM_PAGE_SIZE = 5;
    static final int LINE_ITEM_FOLLOW_UP_PAGE_SIZE = 50;
    // 取消检查只取取消状态与行项目剩余数量：每单 ≈ 1 + 2 + 1 + 10 = 14 点，50 单/页 ≈ 700 点
    static final int CANCEL_CHECK_PAGE_SIZE = 50;
    static final int CANCEL_CHECK_LINE_ITEM_PAGE_SIZE = 10;
    // 单轮最多翻页数，剩余的下一轮继续
    static final int CANCEL_CHECK_MAX_PAGES = 50;
    // 对账单轮时间预算，明显小于执行器每店铺 300 秒超时；到点停止翻页，下一轮从返回的游标继续
    static final long RECONCILE_TIME_BUDGET_MILLIS = 240_000;
    // 自动同步整页都是已存在订单时，本轮最多继续翻的页数（同一秒创建的订单超过一页时避免游标原地踏步）
    static final int AUTO_SKIP_MAX_PAGES = 20;
    // nodes 批量查商品元字段：每个商品 ≈ 1 + 2 + 6 = 9 点
    static final int METAFIELD_BATCH_SIZE = 50;

    private static final String DEFAULT_API_VERSION = "2026-07";
    private static final String METAFIELD_KEY_CN_PRODUCT_NAME = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_CN_PRODUCT_NAME;
    private static final String METAFIELD_KEY_WAYBILL_PRODUCT_NAME = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_WAYBILL_PRODUCT_NAME;
    private static final String METAFIELD_KEY_OWNER_NAME = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_OWNER_NAME;
    private static final String METAFIELD_KEY_OWNER_TELEPHONE = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_OWNER_TELEPHONE;
    private static final String METAFIELD_KEY_SKU_CODE = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_SKU_CODE;
    private static final String METAFIELD_KEY_SKU_CODE_HIGH = ShopifyGraphqlOrderAdapter.METAFIELD_KEY_SKU_CODE_HIGH;
    private static final Pattern LOCALE_PATTERN = Pattern.compile("([a-z]{2})[_-]([A-Z]{2})");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final ZoneOffset ZONE_8 = ZoneOffset.of("+08:00");

    // ==================== GraphQL 查询 ====================

    private static final String MONEY = "{ shopMoney { amount currencyCode } presentmentMoney { amount currencyCode } }";
    private static final String ADDRESS = "{ firstName lastName name company phone address1 address2 city province provinceCode zip country countryCodeV2 }";
    static final String METAFIELDS_SELECTION = "metafields(first: " + ShopifyGraphqlOrderAdapter.METAFIELD_KEYS.size() + ", keys: ["
            + ShopifyGraphqlOrderAdapter.METAFIELD_KEYS.stream()
            .map(key -> "\"" + ShopifyGraphqlOrderAdapter.METAFIELD_NAMESPACE + "." + key + "\"")
            .collect(Collectors.joining(", "))
            + "]) { nodes { namespace key value } }";
    static final String LINE_ITEM_SELECTION = "id name title variantTitle sku quantity currentQuantity"
            + " originalUnitPriceSet " + MONEY
            + " variant { legacyResourceId sku }"
            + " product { legacyResourceId " + METAFIELDS_SELECTION + " }";
    static final String ORDER_SELECTION = "id legacyResourceId name createdAt updatedAt cancelledAt cancelReason closedAt"
            + " displayFinancialStatus displayFulfillmentStatus currencyCode presentmentCurrencyCode"
            + " totalPriceSet " + MONEY
            + " subtotalPriceSet " + MONEY
            + " totalShippingPriceSet " + MONEY
            + " totalTaxSet " + MONEY
            + " totalDiscountsSet " + MONEY
            + " email phone customerLocale note tags sourceName clientIp"
            + " customerJourneySummary { firstVisit { landingPage referrerUrl } lastVisit { landingPage referrerUrl } }"
            + " customAttributes { key value }"
            + " shippingAddress " + ADDRESS
            + " billingAddress " + ADDRESS
            + " paymentGatewayNames"
            + " shippingLines(first: 3) { nodes { title code originalPriceSet " + MONEY + " } }"
            + " lineItems(first: " + LINE_ITEM_PAGE_SIZE + ") { pageInfo { hasNextPage endCursor } nodes { " + LINE_ITEM_SELECTION + " } }";
    private static final String CANCEL_CHECK_LINE_ITEM_SELECTION = "currentQuantity";
    private static final String CANCEL_CHECK_SELECTION = "id legacyResourceId cancelledAt updatedAt"
            + " lineItems(first: " + CANCEL_CHECK_LINE_ITEM_PAGE_SIZE + ") { pageInfo { hasNextPage endCursor } nodes { "
            + CANCEL_CHECK_LINE_ITEM_SELECTION + " } }";
    static final String SORT_CREATED_AT = "CREATED_AT";
    static final String SORT_UPDATED_AT = "UPDATED_AT";
    static final String ORDERS_COUNT_QUERY = "query ShopifyOrdersCount($q: String) { ordersCount(query: $q, limit: null) { count precision } }";
    static final String PRODUCT_METAFIELDS_QUERY = "query ShopifyProductMetafields($ids: [ID!]!) { nodes(ids: $ids) { ... on Product { id legacyResourceId "
            + METAFIELDS_SELECTION + " } } }";

    private final ShopifyGraphqlClient graphqlClient;
    private final RestTemplate restTemplate;
    private final ShopifyAppProperties appProperties;
    private final ShopifyTokenService tokenService;
    private final ShopifyWebsiteStore websiteStore;
    private final ICurrencyService currencyService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final IAddressService addressService;
    private final ITemporaryOrderService temporaryOrderService;
    private final IProductSKUService productSKUService;
    private final SystemUserRepository systemUserRepository;
    private final ThirdPartyWebsiteRepository thirdPartyWebsiteRepository;
    /** 毫秒时钟，测试中可替换以验证对账时间预算 */
    private LongSupplier millisClock = System::currentTimeMillis;

    void setMillisClock(LongSupplier millisClock) {
        this.millisClock = millisClock;
    }

    static String ordersQuery(String sortKey, String selection) {
        return "query ShopifyOrders($first: Int!, $cursor: String, $q: String) { orders(first: $first, after: $cursor, query: $q, sortKey: "
                + sortKey + ", reverse: false) { pageInfo { hasNextPage endCursor } nodes { " + selection + " } } }";
    }

    static String lineItemsQuery(String selection) {
        return "query ShopifyOrderLineItems($id: ID!, $first: Int!, $cursor: String) { order(id: $id) { lineItems(first: $first, after: $cursor)"
                + " { pageInfo { hasNextPage endCursor } nodes { " + selection + " } } } }";
    }

    /**
     * 一页 GraphQL 订单（已转成 REST 形状）
     */
    private record OrderPage(List<JSONObject> orders, String nextCursor) {
    }

    // ==================== 公开接口 ====================

    @Override
    public CountThirdPartyOrderResponse countOrders(ThirdPartyWebsite website, CountThirdPartyOrdersRequest request) {
        ThirdPartyWebsiteDto websiteDto = websiteStore.getDtoById(website.getId());
        prepareAccess(websiteDto);
        Map<String, Object> variables = new HashMap<>();
        variables.put("q", createdAtQuery(request.getCreateAtMin(), request.getCreateAtMax()));
        ShopifyGraphqlClient.GraphqlResult result = graphqlClient.execute(websiteDto, ORDERS_COUNT_QUERY, variables);
        JSONObject count = result.data() == null ? null : result.data().getJSONObject("ordersCount");
        if (count == null || count.getInt("count") == null) {
            throw ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL.newException(String.valueOf(website.getId()), "Shopify ordersCount 返回为空");
        }
        return CountThirdPartyOrderResponse.builder()
                .count(count.getInt("count"))
                .build();
    }

    @Override
    public ShoplineOrderLoadResult loadOrders(ThirdPartyWebsiteDto website, SyncThirdPartyOrdersRequest request, String pageInfo, SyncMode syncMode) {
        boolean isAutoSync = syncMode == SyncMode.AUTO;
        Long websiteId = website.getLongId();
        // 自动同步按 created_at 游标（lastSyncOrderTime）拉取，边界上已存在的订单由 findByOriginOrderId 跳过；lastSyncOrderId 只记录不参与查询
        String query = createdAtQuery(request.getCreateAtMin(), request.getCreateAtMax());
        String cursor = StrUtil.blankToDefault(pageInfo, null);

        ShoplineOrderLoadResult pageResult;
        try {
            prepareAccess(website);
            Map<String, Map<String, String>> metafieldsCache = new HashMap<>();
            OrderPage page = fetchOrderPage(website, SORT_CREATED_AT, query, cursor, metafieldsCache);
            pageResult = page.orders().isEmpty()
                    ? ShoplineOrderLoadResult.empty(page.nextCursor())
                    : convertAndSaveOrders(website, page.orders(), syncMode, page.nextCursor(), metafieldsCache);
            int extraPages = 0;
            while (isAutoSync && pageResult.getNextPageInfo() != null && extraPages++ < AUTO_SKIP_MAX_PAGES
                    && pageResult.getFetchedCount() > 0 && pageResult.getSkippedCount() == pageResult.getFetchedCount()) {
                OrderPage next = fetchOrderPage(website, SORT_CREATED_AT, query, pageResult.getNextPageInfo(), metafieldsCache);
                ShoplineOrderLoadResult nextResult = next.orders().isEmpty()
                        ? ShoplineOrderLoadResult.empty(next.nextCursor())
                        : convertAndSaveOrders(website, next.orders(), syncMode, next.nextCursor(), metafieldsCache);
                pageResult = mergeSkippedPage(pageResult, nextResult);
            }
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                websiteStore.markWebsiteAuthError(websiteId, "Client ID/Secret无效或应用权限不足 (HTTP " + statusCode + ")");
            }
            log.error("Shopify order sync page request failed: websiteId={}, handle={}, syncMode={}, status={}",
                    websiteId, website.getHandle(), syncMode, statusCode, e);
            throw e;
        }
        if (isAutoSync) {
            websiteStore.updateLastSyncInfo(websiteId, pageResult);
        }
        return pageResult;
    }

    @Override
    public ShopifyCancelCheckResult checkCancelledOrders(ThirdPartyWebsiteDto website, LocalDateTime updatedAtMin, String pageInfo) {
        prepareAccess(website);
        String query = ordersQuery(SORT_UPDATED_AT, CANCEL_CHECK_SELECTION);
        String filter = "updated_at:>='" + formatUtc(updatedAtMin) + "'";
        LocalDateTime cursor = updatedAtMin;
        int pages = 0;
        int cancelledCount = 0;
        do {
            JSONObject connection = executeOrders(website, query, filter, pageInfo, CANCEL_CHECK_PAGE_SIZE);
            if (connection == null) {
                throw new IllegalStateException("Shopify cancelled order check response is missing orders");
            }
            JSONArray nodes = ShopifyGraphqlOrderAdapter.connectionNodes(connection);
            if (nodes == null) {
                throw new IllegalStateException("Shopify cancelled order check response is missing orders");
            }
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node == null) {
                    continue;
                }
                if (needsRemainingLineItemsForCancel(node)) {
                    completeLineItems(website, node, CANCEL_CHECK_LINE_ITEM_SELECTION);
                }
                JSONObject order = ShopifyGraphqlOrderAdapter.toRestOrder(node, null);
                String originOrderId = order.getStr("id");
                if (StrUtil.isNotBlank(originOrderId) && isCustomerCancelled(order)
                        && temporaryOrderService.markCustomerCancelled(originOrderId)) {
                    cancelledCount++;
                }
                LocalDateTime updatedAt = parseShopifyDateTime(order.getStr("updated_at"));
                if (updatedAt != null && (cursor == null || updatedAt.isAfter(cursor))) {
                    cursor = updatedAt;
                }
            }
            pageInfo = nextCursor(connection);
        } while (pageInfo != null && ++pages < CANCEL_CHECK_MAX_PAGES);

        if (cancelledCount > 0) {
            log.info("Shopify cancelled orders marked: websiteId={}, handle={}, count={}, updatedAtMin={}, cursor={}",
                    website.getId(), website.getHandle(), cancelledCount, updatedAtMin, cursor);
        }
        return new ShopifyCancelCheckResult(cursor, pageInfo);
    }

    @Override
    public boolean importWebhookOrder(ThirdPartyWebsiteDto website, JSONObject orderPayload) {
        String originOrderId = orderPayload.getStr("id");
        Optional<TemporaryOrder> existing = StrUtil.isBlank(originOrderId) ? Optional.empty()
                : temporaryOrderService.findByOriginOrderId(originOrderId);
        if (existing.isPresent()) {
            // 原始订单 ID 已存在（orders/create 重复或晚于 orders/updated 到达）：按 ID 更新；乱序/重试的旧 payload 跳过
            if (!isNewerThanLocal(orderPayload, existing.get())) {
                log.info("Shopify webhook order skipped, not newer than local: websiteId={}, orderId={}, updatedAt={}",
                        website.getId(), originOrderId, orderPayload.getStr("updated_at"));
                return false;
            }
        } else if (website.isPublicApp() && createdBeforeBinding(orderPayload, bindingBoundary(website))) {
            // 公开应用只自动导入绑定之后创建的订单，绑定前的订单走手动同步（PRD §6）
            log.info("Shopify webhook order skipped, created before binding: websiteId={}, orderId={}", website.getId(), originOrderId);
            return false;
        }
        return convertShopifyOrderToTemporary(website, website.getOwner(), orderPayload, true, new HashMap<>());
    }

    /**
     * 对账（公开应用）。按 updated_at 升序拉取 updated_at >= since 的订单：
     * 不存在的新建（公开应用跳过绑定前创建的订单）；已存在且 Shopify updated_at 比本地记录新的按原始订单 ID 整单更新（含取消状态，
     * 已转正式订单的按 TemporaryOrderService 规则只更新非审单字段），不比本地新的跳过。
     * <p>
     * 游标语义：返回值与落库的 lastReconcileTime 都是「已确认处理完的最大 updated_at」（东八区），
     * updated_at 不晚于它的订单本轮都已处理。每处理完一页落库一次（只前进不后退，起点为 website.lastReconcileTime）；
     * 中途异常或到达时间预算时停止翻页并返回已推进到的游标，不抛出；单个订单失败时游标停在该订单之前，下一轮重试。
     * 无订单时返回 since。
     */
    @Override
    public LocalDateTime reconcileOrders(ThirdPartyWebsiteDto website, LocalDateTime since) {
        prepareAccess(website);
        long startedAt = millisClock.getAsLong();
        String filter = "updated_at:>='" + formatUtc(since) + "'";
        Map<String, Map<String, String>> metafieldsCache = new HashMap<>();
        LocalDateTime boundary = website.isPublicApp() ? bindingBoundary(website) : null;
        LocalDateTime cursor = since;
        LocalDateTime persisted = website.getLastReconcileTime();
        String pageCursor = null;
        int pages = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int beforeBinding = 0;
        String stopReason = null;
        try {
            do {
                if (pages > 0 && millisClock.getAsLong() - startedAt >= RECONCILE_TIME_BUDGET_MILLIS) {
                    stopReason = "time-budget";
                    break;
                }
                OrderPage page = fetchOrderPage(website, SORT_UPDATED_AT, filter, pageCursor, metafieldsCache);
                pages++;
                for (JSONObject order : page.orders()) {
                    String originOrderId = order.getStr("id");
                    try {
                        Optional<TemporaryOrder> existing = StrUtil.isBlank(originOrderId) ? Optional.empty()
                                : temporaryOrderService.findByOriginOrderId(originOrderId);
                        if (existing.isEmpty()) {
                            if (createdBeforeBinding(order, boundary)) {
                                beforeBinding++;
                            } else if (convertShopifyOrderToTemporary(website, website.getOwner(), order, true, metafieldsCache)) {
                                // 允许更新：与 webhook 并发导入同一订单时按原始订单 ID 更新而不是报重复
                                created++;
                            }
                        } else if (isNewerThanLocal(order, existing.get())) {
                            convertShopifyOrderToTemporary(website, website.getOwner(), order, true, metafieldsCache);
                            updated++;
                        } else {
                            skipped++;
                        }
                    } catch (Exception e) {
                        // 按 updated_at 升序处理，失败即停在此前的游标，下一轮重试
                        log.error("Shopify reconcile order failed: websiteId={}, handle={}, orderId={}, orderName={}, updatedAt={}",
                                website.getId(), website.getHandle(), originOrderId, order.getStr("name"), order.getStr("updated_at"), e);
                        stopReason = "order-failed";
                        break;
                    }
                    LocalDateTime updatedAt = parseShopifyDateTime(order.getStr("updated_at"));
                    if (updatedAt != null && (cursor == null || updatedAt.isAfter(cursor))) {
                        cursor = updatedAt;
                    }
                }
                persisted = persistReconcileCursor(website, cursor, persisted);
                pageCursor = page.nextCursor();
            } while (stopReason == null && pageCursor != null);
        } catch (Exception e) {
            log.error("Shopify reconcile page failed, keeping progress: websiteId={}, handle={}, since={}, cursor={}",
                    website.getId(), website.getHandle(), since, cursor, e);
            stopReason = "page-failed";
        }

        log.info("Shopify reconcile finished: websiteId={}, handle={}, since={}, cursor={}, pages={}, created={}, updated={}, skipped={}, beforeBinding={}, stopReason={}",
                website.getId(), website.getHandle(), since, cursor, pages, created, updated, skipped, beforeBinding, stopReason);
        return cursor;
    }

    /**
     * Shopify 订单的 updated_at 是否比本地记录的新；本地没有记录（旧数据）或 Shopify 未返回时视为更新
     */
    private boolean isNewerThanLocal(JSONObject order, TemporaryOrder local) {
        LocalDateTime remote = parseShopifyDateTime(order.getStr("updated_at"));
        return remote == null || local.getOriginUpdatedAt() == null || remote.isAfter(local.getOriginUpdatedAt());
    }

    /**
     * 游标比已落库的值新时落库，返回新的已落库值（只前进不后退）
     */
    private LocalDateTime persistReconcileCursor(ThirdPartyWebsiteDto website, LocalDateTime cursor, LocalDateTime persisted) {
        if (cursor == null || (persisted != null && !cursor.isAfter(persisted))) {
            return persisted;
        }
        websiteStore.updateReconcileTime(website.getLongId(), cursor);
        return cursor;
    }

    /**
     * 绑定边界：公开应用首次绑定时 lastSyncOrderTime = 绑定时间（恢复时保留），公开应用不跑自动同步，之后不会被推进
     */
    private LocalDateTime bindingBoundary(ThirdPartyWebsiteDto website) {
        return thirdPartyWebsiteRepository.findById(website.getLongId())
                .map(ThirdPartyWebsite::getLastSyncOrderTime)
                .orElse(null);
    }

    private boolean createdBeforeBinding(JSONObject order, LocalDateTime boundary) {
        if (boundary == null) {
            return false;
        }
        LocalDateTime createdAt = parseShopifyDateTime(order.getStr("created_at"));
        return createdAt != null && createdAt.isBefore(boundary);
    }

    @Override
    public void verifyAndUpdateAuthStatus(ThirdPartyWebsite website) {
        ShopifyTokenService.ShopifyToken token;
        try {
            token = tokenService.fetchToken(website.getHandle(), website.getClientId(), website.getClientSecret());
        } catch (HttpClientErrorException e) {
            String unavailableMessage = shopUnavailableMessage(e.getStatusCode().value());
            if (unavailableMessage != null) {
                throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(unavailableMessage);
            }
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                    "无法获取Shopify访问令牌，请检查Handle、Client ID、Client Secret是否正确，以及应用是否已安装到该店铺 (HTTP "
                            + e.getStatusCode().value() + ")");
        } catch (ResourceAccessException e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("无法连接到Shopify，请检查Handle是否正确");
        }
        website.setToken(token.accessToken());
        website.setTokenExpiresAt(token.expiresAt());

        // 店铺可能尚未落库（无 id），不经过 ShopifyGraphqlClient（其失败处理会按 id 写库），直接调一次 GraphQL ordersCount 验证
        try {
            JSONObject body = postVerifyQuery(website.getHandle(), token.accessToken());
            JSONArray errors = body.get("errors") instanceof JSONArray array ? array : null;
            JSONObject data = body.getJSONObject("data");
            if (errors != null && !errors.isEmpty()) {
                website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
                website.setAuthMessage(isAccessDenied(errors) ? "应用缺少订单读取权限(read_orders)" : "API错误: " + errors);
            } else if (data != null && data.getJSONObject("ordersCount") != null) {
                website.setAuthStatus(ThirdPartyAuthStatusEnum.AUTHED);
                website.setAuthMessage(null);
            } else {
                website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
                website.setAuthMessage("API响应异常: 未返回订单数");
            }
        } catch (HttpClientErrorException e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            int code = e.getStatusCode().value();
            String unavailableMessage = shopUnavailableMessage(code);
            if (unavailableMessage != null) {
                website.setAuthMessage(unavailableMessage);
                website.setStatus(StatusEnum.INVALID);
            } else if (code == 401) {
                website.setAuthMessage("Token无效或已过期");
            } else if (code == 403) {
                website.setAuthMessage("应用缺少订单读取权限(read_orders)");
            } else {
                website.setAuthMessage("API错误: HTTP " + code);
            }
        } catch (ResourceAccessException e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            website.setAuthMessage("无法连接到Shopify，请检查Handle是否正确");
        } catch (Exception e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            website.setAuthMessage("验证失败: " + e.getMessage());
        }
    }

    // ==================== GraphQL 调用 ====================

    /**
     * 请求前校验 handle 并取 token：token 获取阶段店铺冻结/锁定（402/423）时停用店铺；
     * 请求阶段的 401/402/423/限流由 ShopifyGraphqlClient 处理
     */
    private void prepareAccess(ThirdPartyWebsiteDto website) {
        ShopifyHost.host(website.getHandle());
        try {
            tokenService.getAccessToken(website);
        } catch (HttpClientErrorException e) {
            String unavailableMessage = shopUnavailableMessage(e.getStatusCode().value());
            if (unavailableMessage != null) {
                websiteStore.markWebsiteAuthError(website.getLongId(), unavailableMessage);
            }
            throw e;
        }
    }

    /**
     * 拉取一页完整订单（行项目补全）并转成 REST 形状，内联元字段写入 metafieldsCache
     */
    private OrderPage fetchOrderPage(ThirdPartyWebsiteDto website, String sortKey, String filter, String cursor,
                                     Map<String, Map<String, String>> metafieldsCache) {
        JSONObject connection = executeOrders(website, ordersQuery(sortKey, ORDER_SELECTION), filter, cursor, ORDER_PAGE_SIZE);
        if (connection == null) {
            throw ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL.newException(String.valueOf(website.getLongId()), "Shopify orders 返回为空");
        }
        JSONArray nodes = ShopifyGraphqlOrderAdapter.connectionNodes(connection);
        List<JSONObject> orders = new ArrayList<>(nodes == null ? 0 : nodes.size());
        if (nodes != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node == null) {
                    continue;
                }
                completeLineItems(website, node, LINE_ITEM_SELECTION);
                orders.add(ShopifyGraphqlOrderAdapter.toRestOrder(node, metafieldsCache));
            }
        }
        return new OrderPage(orders, nextCursor(connection));
    }

    private JSONObject executeOrders(ThirdPartyWebsiteDto website, String query, String filter, String cursor, int pageSize) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("first", pageSize);
        variables.put("cursor", cursor);
        variables.put("q", filter);
        ShopifyGraphqlClient.GraphqlResult result = graphqlClient.execute(website, query, variables);
        return result.data() == null ? null : result.data().getJSONObject("orders");
    }

    /**
     * 行项目超过首屏数量时按游标补查剩余行项目，追加到节点的 lineItems.nodes
     */
    private void completeLineItems(ThirdPartyWebsiteDto website, JSONObject orderNode, String selection) {
        JSONObject lineItems = orderNode.getJSONObject("lineItems");
        if (lineItems == null) {
            return;
        }
        JSONArray nodes = ShopifyGraphqlOrderAdapter.connectionNodes(lineItems);
        if (nodes == null) {
            nodes = new JSONArray();
        }
        String cursor = nextCursor(lineItems);
        String query = lineItemsQuery(selection);
        while (cursor != null) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("id", orderNode.getStr("id"));
            variables.put("first", LINE_ITEM_FOLLOW_UP_PAGE_SIZE);
            variables.put("cursor", cursor);
            ShopifyGraphqlClient.GraphqlResult result = graphqlClient.execute(website, query, variables);
            JSONObject order = result.data() == null ? null : result.data().getJSONObject("order");
            JSONObject more = order == null ? null : order.getJSONObject("lineItems");
            if (more == null) {
                throw ServiceResponseEnum.ERR_SHOPIFY_GRAPHQL.newException(String.valueOf(website.getLongId()),
                        "Shopify 订单行项目补查返回为空: " + orderNode.getStr("id"));
            }
            JSONArray moreNodes = ShopifyGraphqlOrderAdapter.connectionNodes(more);
            if (moreNodes != null) {
                nodes.addAll(moreNodes);
            }
            cursor = nextCursor(more);
        }
        JSONObject completed = new JSONObject();
        completed.set("nodes", nodes);
        orderNode.set("lineItems", completed);
    }

    /**
     * 首屏行项目剩余数量全为 0 且还有更多行项目时，才需要补查以判断是否整单移除
     */
    private boolean needsRemainingLineItemsForCancel(JSONObject orderNode) {
        Object cancelledAt = orderNode.get("cancelledAt");
        if (!JSONUtil.isNull(cancelledAt) && cancelledAt != null) {
            return false;
        }
        JSONObject lineItems = orderNode.getJSONObject("lineItems");
        if (lineItems == null || nextCursor(lineItems) == null) {
            return false;
        }
        JSONArray nodes = ShopifyGraphqlOrderAdapter.connectionNodes(lineItems);
        if (nodes == null) {
            return true;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject item = nodes.getJSONObject(i);
            Integer currentQuantity = item == null ? null : item.getInt("currentQuantity");
            if (currentQuantity == null || currentQuantity > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 连接的下一页游标，没有下一页时为 null
     */
    private static String nextCursor(JSONObject connection) {
        JSONObject pageInfo = connection.getJSONObject("pageInfo");
        if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.getBool("hasNextPage"))) {
            return null;
        }
        return StrUtil.blankToDefault(pageInfo.getStr("endCursor"), null);
    }

    private JSONObject postVerifyQuery(String handle, String accessToken) {
        String url = "https://" + ShopifyHost.host(handle) + "/admin/api/" + apiVersion() + "/graphql.json";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Shopify-Access-Token", accessToken);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", ORDERS_COUNT_QUERY);
        requestBody.put("variables", Collections.emptyMap());
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(requestBody, headers), String.class);
        if (StrUtil.isBlank(response.getBody())) {
            throw new IllegalStateException("Shopify GraphQL 返回空响应 (HTTP " + response.getStatusCode().value() + ")");
        }
        return JSONUtil.parseObj(response.getBody());
    }

    private static boolean isAccessDenied(JSONArray errors) {
        for (int i = 0; i < errors.size(); i++) {
            Object item = errors.get(i);
            if (item instanceof JSONObject error) {
                JSONObject extensions = error.getJSONObject("extensions");
                if (extensions != null && "ACCESS_DENIED".equals(extensions.getStr("code"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String apiVersion() {
        String version = appProperties == null ? null : appProperties.getApiVersion();
        return StrUtil.isBlank(version) ? DEFAULT_API_VERSION : version;
    }

    /**
     * created_at 范围的搜索语法（UTC），无边界时为 null（不过滤）
     */
    private String createdAtQuery(LocalDateTime min, LocalDateTime max) {
        List<String> terms = new ArrayList<>(2);
        if (min != null) {
            terms.add("created_at:>='" + formatUtc(min) + "'");
        }
        if (max != null) {
            terms.add("created_at:<='" + formatUtc(max) + "'");
        }
        return terms.isEmpty() ? null : String.join(" ", terms);
    }

    // ==================== 内部方法 ====================

    private String shopUnavailableMessage(int statusCode) {
        return switch (statusCode) {
            case 402 -> "Shopify店铺已冻结或不可用，请登录Shopify后台检查店铺状态及账单 (HTTP 402)";
            case 423 -> "Shopify店铺已锁定，请联系Shopify支持检查店铺状态 (HTTP 423)";
            default -> null;
        };
    }

    private String buildHost(String handle) {
        return ShopifyHost.host(handle);
    }

    /**
     * 自动同步整页跳过后继续翻页：计数累加，游标取两页中较大者，下一页游标取后一页
     */
    private static ShoplineOrderLoadResult mergeSkippedPage(ShoplineOrderLoadResult skippedPage, ShoplineOrderLoadResult next) {
        String cursorOrderId = skippedPage.getCursorOrderId();
        if (next.getCursorOrderId() != null && (cursorOrderId == null || compareOrderId(next.getCursorOrderId(), cursorOrderId) > 0)) {
            cursorOrderId = next.getCursorOrderId();
        }
        LocalDateTime cursorOrderTime = skippedPage.getCursorOrderTime();
        if (next.getCursorOrderTime() != null && (cursorOrderTime == null || next.getCursorOrderTime().isAfter(cursorOrderTime))) {
            cursorOrderTime = next.getCursorOrderTime();
        }
        return ShoplineOrderLoadResult.builder()
                .nextPageInfo(next.getNextPageInfo())
                .fetchedCount(skippedPage.getFetchedCount() + next.getFetchedCount())
                .successCount(skippedPage.getSuccessCount() + next.getSuccessCount())
                .failedCount(skippedPage.getFailedCount() + next.getFailedCount())
                .skippedCount(skippedPage.getSkippedCount() + next.getSkippedCount())
                .createdCount(skippedPage.getCreatedCount() + next.getCreatedCount())
                .cursorOrderId(cursorOrderId)
                .cursorOrderTime(cursorOrderTime)
                .build();
    }

    private ShoplineOrderLoadResult convertAndSaveOrders(ThirdPartyWebsiteDto website, List<JSONObject> orders,
                                                         SyncMode syncMode, String nextPageInfo,
                                                         Map<String, Map<String, String>> metafieldsCache) {
        SystemUserDto owner = website.getOwner();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int createdCount = 0;
        boolean updateExisting = syncMode == SyncMode.MANUAL;

        // 保守策略游标：仅记录已成功处理（created/skipped）的最大 id 与创建时间；
        // 自动同步遇到失败单立即终止本页，手动同步则继续处理并统计本页所有订单。
        String cursorOrderId = null;
        LocalDateTime cursorOrderTime = null;
        boolean abortedByFailure = false;
        int processedIndex = 0;

        for (int i = 0; i < orders.size(); i++) {
            JSONObject order = orders.get(i);
            String originOrderId = order.getStr("id");
            if (log.isDebugEnabled()) {
                log.debug("Shopify order sync raw order\n"
                                + "==================== Shopify Order BEGIN ====================\n"
                                + "websiteId: {} | handle: {} | syncMode: {} | orderIndex: {}/{}\n"
                                + "orderId: {} | orderName: {}\n"
                                + "{}\n"
                                + "===================== Shopify Order END =====================",
                        website.getId(), website.getHandle(), syncMode, i + 1, orders.size(),
                        originOrderId, order.getStr("name"), JSONUtil.toJsonPrettyStr(order));
            }
            try {
                if (!updateExisting && StrUtil.isNotBlank(originOrderId)
                        && temporaryOrderService.findByOriginOrderId(originOrderId).isPresent()) {
                    skippedCount++;
                } else {
                    if (convertShopifyOrderToTemporary(website, owner, order, updateExisting, metafieldsCache)) {
                        createdCount++;
                    }
                    successCount++;
                }
            } catch (Exception e) {
                failedCount++;
                log.error("Shopify order sync failed: websiteId={}, handle={}, syncMode={}, orderIndex={}/{}, orderId={}, orderName={}, createdAt={}, financialStatus={}, fulfillmentStatus={}",
                        website.getId(), website.getHandle(), syncMode, i, orders.size(), order.getStr("id"), order.getStr("name"),
                        order.getStr("created_at"), order.getStr("financial_status"), order.getStr("fulfillment_status"), e);
                if (!updateExisting) {
                    abortedByFailure = true;
                    break;
                }
                continue;
            }
            processedIndex = i + 1;

            if (originOrderId != null && (cursorOrderId == null || compareOrderId(originOrderId, cursorOrderId) > 0)) {
                cursorOrderId = originOrderId;
            }
            LocalDateTime createdAt = parseShopifyDateTime(order.getStr("created_at"));
            if (createdAt != null && (cursorOrderTime == null || createdAt.isAfter(cursorOrderTime))) {
                cursorOrderTime = createdAt;
            }
        }

        if (abortedByFailure) {
            int remaining = orders.size() - processedIndex - 1;
            log.warn("Shopify order sync aborted by failure: websiteId={}, handle={}, syncMode={}, created={}, skipped={}, failed={}, remainingForNextRound={}, cursorOrderId={}",
                    website.getId(), website.getHandle(), syncMode, createdCount, skippedCount, failedCount, remaining, cursorOrderId);
        }
        return ShoplineOrderLoadResult.builder()
                .nextPageInfo(nextPageInfo)
                .fetchedCount(orders.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .createdCount(createdCount)
                .cursorOrderId(cursorOrderId)
                .cursorOrderTime(cursorOrderTime)
                .build();
    }

    private boolean convertShopifyOrderToTemporary(ThirdPartyWebsiteDto website, SystemUserDto owner,
                                                   JSONObject order, boolean updateExisting,
                                                   Map<String, Map<String, String>> metafieldsCache) {
        CurrencyMode currencyMode = website.getCurrencyMode() != null ? website.getCurrencyMode() : CurrencyMode.SHOP_MONEY;
        String moneyKey = currencyMode == CurrencyMode.PRESENTMENT_MONEY ? "presentment_money" : "shop_money";

        Map<String, Map<String, String>> productMetafieldsMap = fetchMetafieldsForLineItems(website, order, metafieldsCache);

        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(owner.getCompanyId());
        request.setFrom(website.getNickName() + "-SHOPIFY");
        request.setFromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""));
        request.setPlatform(WebsiteTypeEnum.SHOPIFY);
        request.setOriginOrderId(order.getStr("id"));
        LocalDateTime orderTime = parseShopifyDateTime(order.getStr("created_at"));
        request.setOrderTime(orderTime != null ? orderTime : LocalDateTime.now());

        request.setDeliveryInfo(buildDeliveryInfo(website, order));
        request.setFinancialInfo(buildFinancialInfo(order, moneyKey));
        request.setPaymentInfo(buildPaymentInfo(order));
        request.setContextInfo(buildContextInfo(website, owner, order, moneyKey));
        TemporaryOrderRiskRecordInfoRequest riskInfo = buildRiskInfo(order);
        log.debug("Shopify order IP/UA: websiteId={}, handle={}, orderId={}, resolvedRemoteIp={}, ua={}",
                website.getId(), website.getHandle(), order.getStr("id"), riskInfo.getRemoteIp(), riskInfo.getUa());
        request.setRiskInfo(riskInfo);
        request.setItemInfos(buildItemInfos(order, moneyKey, owner, productMetafieldsMap));
        request.setCustomerCancelled(isCustomerCancelled(order));
        request.setOriginUpdatedAt(parseShopifyDateTime(order.getStr("updated_at")));

        // 归属人优先级：归属人账号(telephone) > 归属人(name) > 第三方商城归属(website owner)
        applyOwnerFromMetafields(request.getContextInfo(), order, productMetafieldsMap, owner.getCompanyId());

        return temporaryOrderService.synchronizeOrderFromExternalSystem(request, updateExisting);
    }

    private TemporaryOrderDeliveryInfoRequest buildDeliveryInfo(ThirdPartyWebsiteDto website, JSONObject order) {
        TemporaryOrderDeliveryInfoRequest info = new TemporaryOrderDeliveryInfoRequest();
        info.setRemark(StrUtil.blankToDefault(order.getStr("note"), ""));

        JSONObject addr = order.getJSONObject("shipping_address");
        if (addr == null || addr.isEmpty()) {
            addr = order.getJSONObject("billing_address");
        }
        if (addr != null && !addr.isEmpty()) {
            info.setFirstName(StrUtil.blankToDefault(addr.getStr("first_name"), ""));
            info.setLastName(StrUtil.blankToDefault(addr.getStr("last_name"), ""));
            info.setPhone(StrUtil.blankToDefault(addr.getStr("phone"), ""));
            info.setProvince(StrUtil.blankToDefault(addr.getStr("province"), ""));
            info.setCity(StrUtil.blankToDefault(addr.getStr("city"), ""));
            info.setDistrict("");
            info.setPostalCode(StrUtil.blankToDefault(addr.getStr("zip"), ""));
            String address2 = StrUtil.blankToDefault(addr.getStr("address2"), "");
            String address1 = StrUtil.blankToDefault(addr.getStr("address1"), "");
            info.setAddress(address1 + (StrUtil.isNotBlank(address2) ? " /" + address2 : ""));
            if (StrUtil.isBlank(info.getProvince())) {
                fillProvinceFromAddressLibrary(website, order, info, addr.getStr("country_code"));
            }
        }
        // 地址没有电话时用订单联系电话
        if (StrUtil.isBlank(info.getPhone())) {
            info.setPhone(StrUtil.blankToDefault(order.getStr("phone"), ""));
        }

        String email = StrUtil.blankToDefault(order.getStr("email"), order.getStr("contact_email"));
        info.setEmail(StrUtil.blankToDefault(email, ""));
        info.setReceiveUpdates(false);
        info.setRemoteArea(false);
        return info;
    }

    /**
     * Shopify 地址只有城市没有省份时，到对应国家的地址库反查省份（优先城市+邮编，其次只按城市），唯一匹配才补全。
     * 地址库查询失败不影响订单同步。
     */
    private void fillProvinceFromAddressLibrary(ThirdPartyWebsiteDto website, JSONObject order,
                                                TemporaryOrderDeliveryInfoRequest info, String countryCode) {
        if (StrUtil.isBlank(info.getCity()) || StrUtil.isBlank(countryCode)) {
            return;
        }
        try {
            addressService.resolveProvince(countryCode, info.getCity(), info.getPostalCode()).ifPresent(province -> {
                info.setProvince(province);
                log.info("Shopify order province filled from address library: websiteId={}, handle={}, orderId={}, country={}, city={}, postalCode={}, province={}",
                        website.getId(), website.getHandle(), order.getStr("id"), countryCode, info.getCity(), info.getPostalCode(), province);
            });
        } catch (Exception e) {
            log.warn("Shopify order province lookup failed: websiteId={}, handle={}, orderId={}, country={}, city={}",
                    website.getId(), website.getHandle(), order.getStr("id"), countryCode, info.getCity(), e);
        }
    }

    private TemporaryOrderFinancialInfoRequest buildFinancialInfo(JSONObject order, String moneyKey) {
        TemporaryOrderFinancialInfoRequest info = new TemporaryOrderFinancialInfoRequest();
        info.setTotalAmount(extractMoneyAmount(order, "total_price_set", moneyKey, "total_price"));
        info.setDiscountAmount(extractMoneyAmount(order, "total_discounts_set", moneyKey, "total_discounts"));
        info.setTaxAmount(extractMoneyAmount(order, "total_tax_set", moneyKey, "total_tax"));

        BigDecimal shippingFee = BigDecimal.ZERO;
        JSONArray shippingLines = order.getJSONArray("shipping_lines");
        if (shippingLines != null) {
            for (int i = 0; i < shippingLines.size(); i++) {
                JSONObject line = shippingLines.getJSONObject(i);
                if (line == null) {
                    continue;
                }
                JSONObject priceSet = line.getJSONObject("price_set");
                if (priceSet != null) {
                    shippingFee = shippingFee.add(extractAmountFromMoneySet(priceSet, moneyKey));
                } else {
                    shippingFee = shippingFee.add(parseBigDecimal(line.getStr("price")));
                }
            }
        }
        info.setShippingFee(shippingFee);
        return info;
    }

    private TemporaryOrderPaymentInfoRequest buildPaymentInfo(JSONObject order) {
        TemporaryOrderPaymentInfoRequest info = new TemporaryOrderPaymentInfoRequest();
        info.setPaymentMethod(PaymentMethod.COD);
        info.setPaymentStatus(convertPaymentStatus(order.getStr("financial_status")));
        info.setPaymentTime(LocalDateTime.now());
        return info;
    }

    /**
     * Shopify financial_status 转换，voided 及未知状态按待支付处理
     */
    private PaymentStatus convertPaymentStatus(String financialStatus) {
        if (StrUtil.isBlank(financialStatus)) {
            return PaymentStatus.WAIT_PAY;
        }
        return switch (financialStatus.trim().toLowerCase()) {
            case "pending" -> PaymentStatus.PENDING;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "partially_paid" -> PaymentStatus.PARTIALLY_PAID;
            case "paid" -> PaymentStatus.PAID;
            case "partially_refunded" -> PaymentStatus.PARTIALLY_REFUNDED;
            case "refunded" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.WAIT_PAY;
        };
    }

    private TemporaryOrderContextInfoRequest buildContextInfo(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order, String moneyKey) {
        TemporaryOrderContextInfoRequest info = new TemporaryOrderContextInfoRequest();
        info.setSalesUid(owner.getLongId());
        info.setSalesPerson(owner.getName());
        info.setDepartmentId(owner.getDepartmentId());
        info.setDepartment(StrUtil.blankToDefault(owner.getDepartmentName(), ""));
        info.setWebsiteId(website.getLongId());
        info.setWebsiteName(website.getNickName());
        info.setWebsiteUrl(OrderQueryHelper.extractHost("https://" + buildHost(website.getHandle()) + "/admin"));
        info.setAddressRule("");
        info.setPhoneRule("");

        String currencyCode = extractCurrencyCode(order, moneyKey);
        if (StrUtil.isNotBlank(currencyCode)) {
            String code = currencyCode.trim().toUpperCase();
            Optional<Currency> currencyOpt = currencyService.getByCode(code);
            if (currencyOpt.isPresent()) {
                Currency currency = currencyOpt.get();
                info.setCurrencyId(currency.getId());
                info.setCurrencyCode(currency.getCode());
                info.setCurrencySymbol(currency.getSymbol());
                info.setCurrencyName(currency.getName());
                info.setCurrencyFractionDigits(currency.getFractionDigits());
                info.setCurrencyExchangeRate(currency.getExchangeRate());
            } else {
                info.setCurrencyCode(code);
            }
        }

        String customerLocale = order.getStr("customer_locale");
        if (StrUtil.isNotBlank(customerLocale)) {
            Matcher matcher = LOCALE_PATTERN.matcher(customerLocale);
            if (matcher.find()) {
                String langCode = matcher.group(1);
                if (!applyLanguage(info, langCode)) {
                    info.setLanguageCode(langCode.toUpperCase());
                }
            } else {
                applyLanguage(info, customerLocale.trim().toLowerCase());
            }
        }

        JSONObject shippingAddress = order.getJSONObject("shipping_address");
        if (shippingAddress != null) {
            resolveCountry(info, shippingAddress.getStr("country_code"));
        }

        JSONObject billingAddress = order.getJSONObject("billing_address");
        if (billingAddress != null && StrUtil.isBlank(info.getCountryCode())) {
            resolveCountry(info, billingAddress.getStr("country_code"));
        }

        if (StrUtil.isBlank(info.getCountryCode()) && StrUtil.isNotBlank(customerLocale)) {
            Matcher matcher = LOCALE_PATTERN.matcher(customerLocale);
            if (matcher.find()) {
                resolveCountry(info, matcher.group(2));
            }
        }

        return info;
    }

    private boolean applyLanguage(TemporaryOrderContextInfoRequest info, String langCode) {
        Optional<Language> langOpt = languageService.getByCode(langCode);
        if (langOpt.isEmpty()) {
            return false;
        }
        Language language = langOpt.get();
        info.setLanguageId(String.valueOf(language.getId()));
        info.setLanguage(language.getName());
        info.setLanguageCode(language.getCode());
        return true;
    }

    private void resolveCountry(TemporaryOrderContextInfoRequest info, String countryCode) {
        if (StrUtil.isBlank(countryCode)) {
            return;
        }
        String code = countryCode.trim().toUpperCase();
        Optional<Country> countryOpt = countryService.getByCode(code);
        if (countryOpt.isPresent()) {
            Country country = countryOpt.get();
            info.setCountryId(country.getId());
            info.setCountry(country.getName());
            info.setCountryCode(country.getCode());
        } else {
            info.setCountryCode(code);
            info.setCountry(code);
        }
    }

    private TemporaryOrderRiskRecordInfoRequest buildRiskInfo(JSONObject order) {
        TemporaryOrderRiskRecordInfoRequest info = new TemporaryOrderRiskRecordInfoRequest();
        JSONObject clientDetails = order.getJSONObject("client_details");
        String browserIp = order.getStr("browser_ip");
        String ua = "";
        if (clientDetails != null) {
            ua = StrUtil.blankToDefault(clientDetails.getStr("user_agent"), "");
            browserIp = StrUtil.blankToDefault(clientDetails.getStr("browser_ip"), browserIp);
        }
        // EasySell 等表单应用会把客户 IP 放在订单的附加详情中。
        if (StrUtil.isBlank(browserIp)) {
            JSONArray noteAttributes = order.getJSONArray("note_attributes");
            if (noteAttributes != null) {
                for (int i = 0; i < noteAttributes.size(); i++) {
                    JSONObject attribute = noteAttributes.getJSONObject(i);
                    if (attribute != null && "IP Address".equalsIgnoreCase(StrUtil.trim(attribute.getStr("name")))) {
                        String attributeIp = StrUtil.trim(attribute.getStr("value"));
                        if (StrUtil.isNotBlank(attributeIp)) {
                            browserIp = attributeIp;
                            break;
                        }
                    }
                }
            }
        }
        info.setRemoteIp(StrUtil.blankToDefault(browserIp, ""));
        info.setUa(ua);
        // 后台建单、POS 等渠道的订单没有 UA
        info.setBrowserPlatform(StrUtil.isBlank(ua) ? BrowserPlatform.UNKNOWN : BrowserPlatform.fromUaStr(ua));
        return info;
    }

    /**
     * 根据第一个商品行的 metafield 归属人信息覆盖 contextInfo 的销售归属。
     * 优先级：归属人账号(telephone) > 归属人(name) > 第三方商城归属(website owner，即当前默认值)。
     */
    private void applyOwnerFromMetafields(TemporaryOrderContextInfoRequest contextInfo,
                                          JSONObject order,
                                          Map<String, Map<String, String>> productMetafieldsMap, Long companyId) {
        if (contextInfo == null || productMetafieldsMap.isEmpty()) {
            return;
        }
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return;
        }
        Map<String, String> firstMetafields = null;
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem == null) {
                continue;
            }
            String pid = lineItem.getStr("product_id");
            if (StrUtil.isNotBlank(pid) && productMetafieldsMap.containsKey(pid)) {
                firstMetafields = productMetafieldsMap.get(pid);
                break;
            }
        }
        if (firstMetafields == null || firstMetafields.isEmpty()) {
            return;
        }

        String ownerTelephone = firstMetafields.get(METAFIELD_KEY_OWNER_TELEPHONE);
        String ownerName = firstMetafields.get(METAFIELD_KEY_OWNER_NAME);

        SystemUser resolvedOwner = null;
        if (StrUtil.isNotBlank(ownerTelephone)) {
            List<SystemUser> users = systemUserRepository.findByTelephoneAndCompanyIdWithDepartment(ownerTelephone.trim(), companyId, PageRequest.of(0, 1));
            resolvedOwner = users.isEmpty() ? null : users.get(0);
        }
        if (resolvedOwner == null && StrUtil.isNotBlank(ownerName)) {
            List<SystemUser> owners = systemUserRepository.findByUserNameAndCompanyIdWithDepartment(ownerName.trim(), companyId, PageRequest.of(0, 1));
            resolvedOwner = owners.isEmpty() ? null : owners.get(0);
        }

        if (resolvedOwner != null) {
            contextInfo.setSalesUid(resolvedOwner.getId());
            contextInfo.setSalesPerson(resolvedOwner.getName());
            if (resolvedOwner.getDepartment() != null) {
                contextInfo.setDepartmentId(resolvedOwner.getDepartment().getId());
                contextInfo.setDepartment(resolvedOwner.getDepartment().getName());
            }
        }
    }

    private List<TemporaryOrderItemInfoRequest> buildItemInfos(JSONObject order, String moneyKey, SystemUserDto owner,
                                                               Map<String, Map<String, String>> productMetafieldsMap) {
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return List.of();
        }

        List<String> skuCodes = new ArrayList<>();
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem != null) {
                String code = resolveSkuCode(lineItem, productMetafieldsMap);
                if (StrUtil.isNotBlank(code)) {
                    skuCodes.add(code.trim());
                }
            }
        }

        Map<String, ProductSKU> skuMap = new HashMap<>();
        if (!skuCodes.isEmpty()) {
            List<ProductSKU> skuList = productSKUService.listBySkuCodes(skuCodes, owner.getLongId());
            if (skuList.isEmpty()) {
                skuList = productSKUService.listBySkuCodesAndOwnerId(skuCodes, owner.getLongId());
            }
            for (ProductSKU sku : skuList) {
                skuMap.put(sku.getSkuCode(), sku);
            }
        }

        List<TemporaryOrderItemInfoRequest> items = new ArrayList<>(lineItems.size());
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem == null) {
                continue;
            }

            TemporaryOrderItemInfoRequest item = new TemporaryOrderItemInfoRequest();
            item.setSpuId("0");
            item.setProductId("0");
            item.setTitle(StrUtil.blankToDefault(lineItem.getStr("title"), ""));
            item.setSpecTitle(StrUtil.blankToDefault(lineItem.getStr("variant_title"), ""));
            // Shopify 订单商品行不包含图片
            item.setImage("");

            JSONObject priceSet = lineItem.getJSONObject("price_set");
            if (priceSet != null) {
                item.setSellPrice(extractAmountFromMoneySet(priceSet, moneyKey));
            } else {
                item.setSellPrice(parseBigDecimal(lineItem.getStr("price")));
            }

            item.setOriginPrice(BigDecimal.ZERO);
            item.setCostPrice(BigDecimal.ZERO);
            item.setTax(BigDecimal.ZERO);
            item.setBarcode("");
            // 按下单时的数量原样接入，不使用订单编辑/退款后的 current_quantity
            item.setQuantity(lineItem.getInt("quantity", 0));

            String skuCode = resolveSkuCode(lineItem, productMetafieldsMap);
            item.setSkuCode(skuCode);
            ProductSKU matchedSku = skuMap.get(skuCode);
            if (matchedSku != null) {
                item.setSkuId(matchedSku.getId());
                item.setSkuName(matchedSku.getName());
            } else {
                item.setSkuId(0L);
                item.setSkuName("");
            }
            item.setSkuIsVirtual(false);
            item.setMerchandise(StrUtil.blankToDefault(lineItem.getStr("title"), ""));

            String shopifyProductId = lineItem.getStr("product_id");
            if (StrUtil.isNotBlank(shopifyProductId)) {
                Map<String, String> metafields = productMetafieldsMap.getOrDefault(shopifyProductId, Map.of());
                String cnProductName = metafields.get(METAFIELD_KEY_CN_PRODUCT_NAME);
                if (StrUtil.isNotBlank(cnProductName)) {
                    item.setMerchandise(cnProductName);
                }
                String waybillName = metafields.get(METAFIELD_KEY_WAYBILL_PRODUCT_NAME);
                if (StrUtil.isNotBlank(waybillName)) {
                    item.setWaybillProductName(waybillName);
                }
            }

            items.add(item);
        }
        return items;
    }

    /**
     * 客户取消：整单已取消（cancelled_at 有值），或全部商品行都被编辑删除/退款（current_quantity 均为 0）。
     * 部分商品被移除不算取消。
     */
    private boolean isCustomerCancelled(JSONObject order) {
        Object cancelledAt = order.get("cancelled_at");
        if (!JSONUtil.isNull(cancelledAt) && StrUtil.isNotBlank(String.valueOf(cancelledAt))) {
            return true;
        }
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return false;
        }
        boolean hasLineItem = false;
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem == null) {
                continue;
            }
            hasLineItem = true;
            Integer currentQuantity = lineItem.getInt("current_quantity");
            if (currentQuantity == null || currentQuantity > 0) {
                return false;
            }
        }
        return hasLineItem;
    }

    /**
     * SKU 编码优先级：metafield sku_code_high > 商品行 sku > metafield sku_code
     */
    private String resolveSkuCode(JSONObject lineItem, Map<String, Map<String, String>> productMetafieldsMap) {
        String skuCode = StrUtil.blankToDefault(lineItem.getStr("sku"), "").trim();
        String shopifyProductId = lineItem.getStr("product_id");
        if (StrUtil.isBlank(shopifyProductId)) {
            return skuCode;
        }
        Map<String, String> metafields = productMetafieldsMap.getOrDefault(shopifyProductId, Map.of());
        String highPrioritySkuCode = metafields.get(METAFIELD_KEY_SKU_CODE_HIGH);
        if (StrUtil.isNotBlank(highPrioritySkuCode)) {
            return highPrioritySkuCode.trim();
        }
        String fallbackSkuCode = metafields.get(METAFIELD_KEY_SKU_CODE);
        if (StrUtil.isBlank(skuCode) && StrUtil.isNotBlank(fallbackSkuCode)) {
            return fallbackSkuCode.trim();
        }
        return skuCode;
    }

    // ==================== Metafield 相关 ====================

    /**
     * 收集订单 line_items 中所有不重复的 product_id 并取其元字段。
     * 订单查询已内联返回的元字段在缓存里直接使用；未命中（webhook 路径）的用 GraphQL nodes 批量查询，查询失败降级为空。
     */
    private Map<String, Map<String, String>> fetchMetafieldsForLineItems(ThirdPartyWebsiteDto website, JSONObject order,
                                                                         Map<String, Map<String, String>> metafieldsCache) {
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return Map.of();
        }
        Set<String> productIds = new LinkedHashSet<>();
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem != null) {
                String pid = lineItem.getStr("product_id");
                if (StrUtil.isNotBlank(pid)) {
                    productIds.add(pid);
                }
            }
        }
        List<String> missing = productIds.stream().filter(id -> !metafieldsCache.containsKey(id)).toList();
        for (int from = 0; from < missing.size(); from += METAFIELD_BATCH_SIZE) {
            List<String> batch = missing.subList(from, Math.min(from + METAFIELD_BATCH_SIZE, missing.size()));
            metafieldsCache.putAll(fetchProductMetafields(website, batch));
        }
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, metafieldsCache.getOrDefault(productId, Map.of()));
        }
        return result;
    }

    /**
     * 用 nodes(ids:) 批量查商品元字段，返回每个请求的商品（查不到的为空 Map）。失败时降级返回空 Map。
     */
    private Map<String, Map<String, String>> fetchProductMetafields(ThirdPartyWebsiteDto website, List<String> productIds) {
        Map<String, Map<String, String>> result = new HashMap<>();
        productIds.forEach(id -> result.put(id, Map.of()));
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("ids", productIds.stream().map(ShopifyGraphqlOrderAdapter::productGid).toList());
            ShopifyGraphqlClient.GraphqlResult response = graphqlClient.execute(website, PRODUCT_METAFIELDS_QUERY, variables);
            JSONArray nodes = response.data() == null ? null : response.data().getJSONArray("nodes");
            if (nodes == null) {
                return result;
            }
            for (int i = 0; i < nodes.size(); i++) {
                Object item = nodes.get(i);
                if (!(item instanceof JSONObject product)) {
                    continue;
                }
                String productId = StrUtil.blankToDefault(product.getStr("legacyResourceId"),
                        ShopifyGraphqlOrderAdapter.legacyId(product.getStr("id")));
                if (StrUtil.isNotBlank(productId)) {
                    result.put(productId, ShopifyGraphqlOrderAdapter.parseMetafields(product.getJSONObject("metafields")));
                }
            }
        } catch (Exception e) {
            log.warn("Shopify fetchMetafields failed: websiteId={}, productIds={}, error={}", website.getId(), productIds, e.getMessage());
        }
        return result;
    }

    // ==================== 工具方法 ====================

    private static int compareOrderId(String a, String b) {
        try {
            return new BigInteger(a).compareTo(new BigInteger(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    /**
     * 从订单的 _set 字段中提取指定币种的金额，fallback 到顶层字段
     */
    private BigDecimal extractMoneyAmount(JSONObject order, String setField, String moneyKey, String fallbackField) {
        JSONObject priceSet = order.getJSONObject(setField);
        if (priceSet != null) {
            return extractAmountFromMoneySet(priceSet, moneyKey);
        }
        return parseBigDecimal(order.getStr(fallbackField));
    }

    private BigDecimal extractAmountFromMoneySet(JSONObject moneySet, String moneyKey) {
        JSONObject money = moneySet.getJSONObject(moneyKey);
        if (money != null) {
            return parseBigDecimal(money.getStr("amount"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * 根据 moneyKey 提取币种，店铺结算币种 fallback 到 currency，订单展示币种 fallback 到 presentment_currency
     */
    private String extractCurrencyCode(JSONObject order, String moneyKey) {
        JSONObject totalPriceSet = order.getJSONObject("total_price_set");
        if (totalPriceSet != null) {
            JSONObject money = totalPriceSet.getJSONObject(moneyKey);
            if (money != null) {
                String code = money.getStr("currency_code");
                if (StrUtil.isNotBlank(code)) {
                    return code;
                }
            }
        }
        if ("presentment_money".equals(moneyKey)) {
            return StrUtil.blankToDefault(order.getStr("presentment_currency"), order.getStr("currency"));
        }
        return order.getStr("currency");
    }

    /**
     * Shopify 返回的时间带店铺时区，统一换算为东八区时间
     */
    private LocalDateTime parseShopifyDateTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateStr, ISO_OFFSET).withOffsetSameInstant(ZONE_8).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Shopify datetime: {}", dateStr, e);
            return null;
        }
    }

    /**
     * 东八区时间转为 UTC 字符串，避免 +08:00 中的加号在 URL 中被当作空格
     */
    private String formatUtc(LocalDateTime localDateTime) {
        return localDateTime.atOffset(ZONE_8).withOffsetSameInstant(ZoneOffset.UTC).format(UTC_FORMAT);
    }

    private BigDecimal parseBigDecimal(String value) {
        if (StrUtil.isBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
