package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import cn.v7soft.admin.service.ICountryService;
import cn.v7soft.admin.service.ICurrencyService;
import cn.v7soft.admin.service.ILanguageService;
import cn.v7soft.admin.service.IProductSKUService;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.ITemporaryOrderService;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.BrowserPlatform;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.PaymentMethod;
import cn.v7soft.dao.enums.PaymentStatus;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shopify 订单同步（REST Admin API）。
 * 与 Shopline 的实现（ThirdPartyWebsiteService）相互独立，订单转换逻辑按 Shopify 的字段单独维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOrderSyncService implements IShopifyOrderSyncService {
    private static final String API_VERSION = "2026-07";
    private static final String PAGE_LIMIT = "100";
    private static final String METAFIELD_NAMESPACE = "xyz";
    private static final String METAFIELD_KEY_CN_PRODUCT_NAME = "cn_product_name";
    private static final String METAFIELD_KEY_WAYBILL_PRODUCT_NAME = "waybill_product_name";
    private static final String METAFIELD_KEY_OWNER_NAME = "owner_name";
    private static final String METAFIELD_KEY_OWNER_TELEPHONE = "owner_telephone";
    private static final String METAFIELD_KEY_SKU_CODE = "sku_code";
    private static final String METAFIELD_KEY_SKU_CODE_HIGH = "sku_code_high";
    private static final Pattern LINK_PAGE_INFO_PATTERN = Pattern.compile("<[^>]*[?&]page_info=([^&>]+)[^>]*>;\\s*rel=\"next\"");
    private static final Pattern LOCALE_PATTERN = Pattern.compile("([a-z]{2})[_-]([A-Z]{2})");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final ZoneOffset ZONE_8 = ZoneOffset.of("+08:00");

    private final RestTemplate restTemplate;
    private final ShopifyTokenService tokenService;
    private final ShopifyWebsiteStore websiteStore;
    private final ICurrencyService currencyService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final ITemporaryOrderService temporaryOrderService;
    private final IProductSKUService productSKUService;
    private final SystemUserRepository systemUserRepository;

    /**
     * 一次调用使用的凭证，token 被拒绝并刷新后会更新
     */
    private static class ApiSession {
        private final Long websiteId;
        private final String handle;
        private String token;

        private ApiSession(Long websiteId, String handle, String token) {
            this.websiteId = websiteId;
            this.handle = handle;
            this.token = token;
        }
    }

    // ==================== 公开接口 ====================

    @Override
    public CountThirdPartyOrderResponse countOrders(ThirdPartyWebsite website, CountThirdPartyOrdersRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(buildApiUrl(website.getHandle(), "orders/count.json"))
                .queryParam("status", "any");
        if (request.getCreateAtMin() != null) {
            builder.queryParam("created_at_min", formatUtc(request.getCreateAtMin()));
        }
        if (request.getCreateAtMax() != null) {
            builder.queryParam("created_at_max", formatUtc(request.getCreateAtMax()));
        }
        URI uri = builder.build().toUri();

        ThirdPartyWebsiteDto websiteDto = websiteStore.getDtoById(website.getId());
        ApiSession session = new ApiSession(website.getId(), website.getHandle(), tokenService.getAccessToken(websiteDto));
        ResponseEntity<String> response = get(session, uri);

        String errors = "status: " + response.getStatusCode();
        if (StrUtil.isNotBlank(response.getBody())) {
            JSONObject body = JSONUtil.parseObj(response.getBody());
            if (body.containsKey("count")) {
                return CountThirdPartyOrderResponse.builder()
                        .count(body.get("count", Integer.class))
                        .build();
            }
            if (body.containsKey("errors")) {
                errors = body.getStr("errors");
            }
        }
        throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(website.getId(), errors);
    }

    @Override
    public ShoplineOrderLoadResult loadOrders(ThirdPartyWebsiteDto website, SyncThirdPartyOrdersRequest request, String pageInfo, SyncMode syncMode) {
        boolean isAutoSync = syncMode == SyncMode.AUTO;
        Long websiteId = website.getLongId();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(buildApiUrl(website.getHandle(), "orders.json"));
        if (StrUtil.isNotBlank(pageInfo)) {
            // 翻页时只允许携带 page_info 与 limit
            builder.queryParam("page_info", pageInfo);
        } else {
            builder.queryParam("status", "any");
            if (request.getCreateAtMin() != null) {
                builder.queryParam("created_at_min", formatUtc(request.getCreateAtMin()));
            }
            if (request.getCreateAtMax() != null) {
                builder.queryParam("created_at_max", formatUtc(request.getCreateAtMax()));
            }
            if (isAutoSync && StrUtil.isNotBlank(website.getLastSyncOrderId())) {
                // 携带 since_id 时按 id 升序返回
                builder.queryParam("since_id", website.getLastSyncOrderId());
            } else {
                builder.queryParam("order", "created_at asc");
            }
        }
        builder.queryParam("limit", PAGE_LIMIT);
        URI uri = builder.build().toUri();

        ApiSession session = new ApiSession(websiteId, website.getHandle(), null);
        ResponseEntity<String> response;
        try {
            session.token = tokenService.getAccessToken(website);
            response = get(session, uri);
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                websiteStore.markWebsiteAuthError(websiteId, "Client ID/Secret无效或应用权限不足 (HTTP " + statusCode + ")");
            }
            log.error("Shopify order sync page request failed: websiteId={}, handle={}, syncMode={}, status={}, uri={}",
                    websiteId, website.getHandle(), syncMode, statusCode, uri, e);
            throw e;
        }

        if (StrUtil.isBlank(response.getBody())) {
            return ShoplineOrderLoadResult.empty(null);
        }

        JSONObject body = JSONUtil.parseObj(response.getBody());
        if (body.containsKey("errors")) {
            throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(websiteId, body.getStr("errors"));
        }

        JSONArray orders = body.getJSONArray("orders");
        String nextPageInfo = extractNextPageInfo(response.getHeaders());
        ShoplineOrderLoadResult pageResult = ShoplineOrderLoadResult.empty(nextPageInfo);
        if (orders != null && !orders.isEmpty()) {
            pageResult = convertAndSaveOrders(website, session, orders, syncMode, nextPageInfo);
        }
        if (isAutoSync) {
            websiteStore.updateLastSyncInfo(websiteId, pageResult);
        }
        return pageResult;
    }

    @Override
    public void verifyAndUpdateAuthStatus(ThirdPartyWebsite website) {
        ShopifyTokenService.ShopifyToken token;
        try {
            token = tokenService.fetchToken(website.getHandle(), website.getClientId(), website.getClientSecret());
        } catch (HttpClientErrorException e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(
                    "无法获取Shopify访问令牌，请检查Handle、Client ID、Client Secret是否正确，以及应用是否已安装到该店铺 (HTTP "
                            + e.getStatusCode().value() + ")");
        } catch (ResourceAccessException e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException("无法连接到Shopify，请检查Handle是否正确");
        }
        website.setToken(token.accessToken());
        website.setTokenExpiresAt(token.expiresAt());

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(buildApiUrl(website.getHandle(), "orders/count.json"))
                    .queryParam("status", "any")
                    .build().toUri();
            ResponseEntity<String> response = callShopifyApi(website.getHandle(),
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(token.accessToken()), String.class));

            if (response.getStatusCode().is2xxSuccessful()) {
                website.setAuthStatus(ThirdPartyAuthStatusEnum.AUTHED);
                website.setAuthMessage(null);
            } else {
                website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
                website.setAuthMessage("API响应异常: HTTP " + response.getStatusCode().value());
            }
        } catch (HttpClientErrorException e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            int code = e.getStatusCode().value();
            if (code == 401) {
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

    // ==================== 内部方法 ====================

    private String buildHost(String handle) {
        return handle + ".myshopify.com";
    }

    private String buildApiUrl(String handle, String endpoint) {
        return "https://" + buildHost(handle) + "/admin/api/" + API_VERSION + "/" + endpoint;
    }

    private HttpEntity<String> buildHttpEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Shopify-Access-Token", token);
        return new HttpEntity<>(headers);
    }

    /**
     * GET 请求，token 被拒绝（401）时刷新 token 后重试一次
     */
    private ResponseEntity<String> get(ApiSession session, URI uri) {
        try {
            return callShopifyApi(session.handle,
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(session.token), String.class));
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Shopify token rejected, refreshing: websiteId={}, handle={}", session.websiteId, session.handle);
            session.token = tokenService.forceRefresh(session.websiteId, session.token);
            return callShopifyApi(session.handle,
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(session.token), String.class));
        }
    }

    private String extractNextPageInfo(HttpHeaders headers) {
        List<String> linkHeaders = headers.get("link");
        if (linkHeaders == null || linkHeaders.isEmpty()) {
            return null;
        }
        for (String link : linkHeaders) {
            Matcher matcher = LINK_PAGE_INFO_PATTERN.matcher(link);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private ShoplineOrderLoadResult convertAndSaveOrders(ThirdPartyWebsiteDto website, ApiSession session, JSONArray orders,
                                                         SyncMode syncMode, String nextPageInfo) {
        SystemUserDto owner = website.getOwner();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int createdCount = 0;
        boolean updateExisting = syncMode == SyncMode.MANUAL;

        // 保守策略游标：仅记录已成功处理（created/skipped）的最大 id；
        // 一旦遇到失败单立即终止本页处理，剩余订单留待下一轮重新拉取，避免失败单被游标跳过造成漏单。
        String cursorOrderId = null;
        LocalDateTime cursorOrderTime = null;
        boolean abortedByFailure = false;
        int processedIndex = 0;

        // 同一页内相同商品的 metafields 只请求一次
        Map<String, Map<String, String>> metafieldsCache = new HashMap<>();

        for (int i = 0; i < orders.size(); i++) {
            JSONObject order = orders.getJSONObject(i);
            String originOrderId = order.getStr("id");
            try {
                if (!updateExisting && StrUtil.isNotBlank(originOrderId)
                        && temporaryOrderService.findByOriginOrderId(originOrderId).isPresent()) {
                    skippedCount++;
                } else {
                    if (convertShopifyOrderToTemporary(website, session, owner, order, updateExisting, metafieldsCache)) {
                        createdCount++;
                    }
                    successCount++;
                }
            } catch (Exception e) {
                failedCount++;
                abortedByFailure = true;
                log.error("Shopify order sync failed: websiteId={}, handle={}, syncMode={}, orderIndex={}/{}, orderId={}, orderName={}, createdAt={}, financialStatus={}, fulfillmentStatus={}",
                        website.getId(), website.getHandle(), syncMode, i, orders.size(), order.getStr("id"), order.getStr("name"),
                        order.getStr("created_at"), order.getStr("financial_status"), order.getStr("fulfillment_status"), e);
                break;
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

    private boolean convertShopifyOrderToTemporary(ThirdPartyWebsiteDto website, ApiSession session, SystemUserDto owner,
                                                   JSONObject order, boolean updateExisting,
                                                   Map<String, Map<String, String>> metafieldsCache) {
        CurrencyMode currencyMode = website.getCurrencyMode() != null ? website.getCurrencyMode() : CurrencyMode.SHOP_MONEY;
        String moneyKey = currencyMode == CurrencyMode.PRESENTMENT_MONEY ? "presentment_money" : "shop_money";

        Map<String, Map<String, String>> productMetafieldsMap = fetchMetafieldsForLineItems(session, order, metafieldsCache);

        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(owner.getCompanyId());
        request.setFrom(website.getNickName() + "-SHOPIFY");
        request.setFromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""));
        request.setPlatform(WebsiteTypeEnum.SHOPIFY);
        request.setOriginOrderId(order.getStr("id"));
        LocalDateTime orderTime = parseShopifyDateTime(order.getStr("created_at"));
        request.setOrderTime(orderTime != null ? orderTime : LocalDateTime.now());

        request.setDeliveryInfo(buildDeliveryInfo(order));
        request.setFinancialInfo(buildFinancialInfo(order, moneyKey));
        request.setPaymentInfo(buildPaymentInfo(order));
        request.setContextInfo(buildContextInfo(website, owner, order, moneyKey));
        request.setRiskInfo(buildRiskInfo(order));
        request.setItemInfos(buildItemInfos(order, moneyKey, owner, productMetafieldsMap));

        // 归属人优先级：归属人账号(telephone) > 归属人(name) > 第三方商城归属(website owner)
        applyOwnerFromMetafields(request.getContextInfo(), order, productMetafieldsMap);

        return temporaryOrderService.synchronizeOrderFromExternalSystem(request, updateExisting);
    }

    private TemporaryOrderDeliveryInfoRequest buildDeliveryInfo(JSONObject order) {
        TemporaryOrderDeliveryInfoRequest info = new TemporaryOrderDeliveryInfoRequest();
        info.setRemark(StrUtil.blankToDefault(order.getStr("note"), ""));

        JSONObject customer = order.getJSONObject("customer");
        JSONObject addr = order.getJSONObject("shipping_address");
        if (addr != null) {
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
        } else if (customer != null) {
            info.setFirstName(StrUtil.blankToDefault(customer.getStr("first_name"), ""));
            info.setLastName(StrUtil.blankToDefault(customer.getStr("last_name"), ""));
            info.setPhone(StrUtil.blankToDefault(customer.getStr("phone"), ""));
        }

        String email = customer != null ? customer.getStr("email") : null;
        if (StrUtil.isBlank(email)) {
            email = StrUtil.blankToDefault(order.getStr("email"), order.getStr("contact_email"));
        }
        info.setEmail(StrUtil.blankToDefault(email, ""));
        info.setReceiveUpdates(false);
        info.setRemoteArea(false);
        return info;
    }

    private TemporaryOrderFinancialInfoRequest buildFinancialInfo(JSONObject order, String moneyKey) {
        TemporaryOrderFinancialInfoRequest info = new TemporaryOrderFinancialInfoRequest();
        info.setTotalAmount(extractMoneyAmount(order, "current_total_price_set", moneyKey, "current_total_price"));
        info.setDiscountAmount(extractMoneyAmount(order, "current_total_discounts_set", moneyKey, "current_total_discounts"));
        info.setTaxAmount(extractMoneyAmount(order, "current_total_tax_set", moneyKey, "current_total_tax"));

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
                resolveCountry(info, matcher.group(2));
            } else {
                applyLanguage(info, customerLocale.trim().toLowerCase());
            }
        }

        JSONObject shippingAddress = order.getJSONObject("shipping_address");
        if (shippingAddress != null && info.getCountryId() == null) {
            resolveCountry(info, shippingAddress.getStr("country_code"));
        }

        JSONObject billingAddress = order.getJSONObject("billing_address");
        if (billingAddress != null && info.getCountryId() == null) {
            resolveCountry(info, billingAddress.getStr("country_code"));
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
                                          Map<String, Map<String, String>> productMetafieldsMap) {
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
            List<SystemUser> users = systemUserRepository.findByTelephoneWithDepartment(ownerTelephone.trim(), PageRequest.of(0, 1));
            resolvedOwner = users.isEmpty() ? null : users.get(0);
        }
        if (resolvedOwner == null && StrUtil.isNotBlank(ownerName)) {
            List<SystemUser> owners = systemUserRepository.findByUserNameWithDepartment(ownerName.trim(), PageRequest.of(0, 1));
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
            item.setQuantity(Integer.parseInt(StrUtil.blankToDefault(lineItem.getStr("quantity"), "0")));

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

    // ==================== Shopify API 限流 & 重试 ====================

    // REST Admin API 为漏桶限流：桶容量 40，每秒恢复 2 个请求
    private static final int SHOPIFY_RATE_LIMIT_PER_SECOND = 2;
    private static final int SHOPIFY_MAX_RETRIES = 3;
    private static final Duration SHOPIFY_RETRY_WAIT = Duration.ofSeconds(1);

    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final Retry shopifyRetry = Retry.of("shopify-api", RetryConfig.custom()
            .maxAttempts(SHOPIFY_MAX_RETRIES)
            .waitDuration(SHOPIFY_RETRY_WAIT)
            .retryOnException(e -> e instanceof HttpClientErrorException.TooManyRequests
                    || e instanceof ResourceAccessException)
            .build());

    private RateLimiter getRateLimiter(String handle) {
        return rateLimiters.computeIfAbsent(handle, h -> RateLimiter.of("shopify-" + h, RateLimiterConfig.custom()
                .limitForPeriod(SHOPIFY_RATE_LIMIT_PER_SECOND)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build()));
    }

    /**
     * 统一包裹 Shopify API 调用：限流 + 429 重试。
     */
    private <T> T callShopifyApi(String handle, Supplier<T> apiCall) {
        RateLimiter limiter = getRateLimiter(handle);
        Supplier<T> decorated = Retry.decorateSupplier(shopifyRetry, RateLimiter.decorateSupplier(limiter, apiCall));
        return decorated.get();
    }

    // ==================== Metafield 相关 ====================

    /**
     * 收集订单 line_items 中所有不重复的 product_id 并获取 metafields，已在缓存中的商品不再请求。
     */
    private Map<String, Map<String, String>> fetchMetafieldsForLineItems(ApiSession session, JSONObject order,
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
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, metafieldsCache.computeIfAbsent(productId, id -> fetchProductMetafields(session, id)));
        }
        return result;
    }

    /**
     * 获取单个商品的 metafields，受限流 + 重试保护。失败时降级返回空 Map。
     */
    private Map<String, String> fetchProductMetafields(ApiSession session, String productId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(buildApiUrl(session.handle, "products/" + productId + "/metafields.json"))
                .queryParam("namespace", METAFIELD_NAMESPACE)
                .build().toUri();
        try {
            return parseMetafieldResponse(get(session, uri).getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Shopify fetchMetafields 429 exhausted retries: productId={}", productId);
        } catch (HttpClientErrorException e) {
            log.warn("Shopify fetchMetafields HTTP error: productId={}, status={}", productId, e.getStatusCode());
        } catch (Exception e) {
            log.warn("Shopify fetchMetafields failed: productId={}, error={}", productId, e.getMessage());
        }
        return Map.of();
    }

    private Map<String, String> parseMetafieldResponse(String body) {
        if (StrUtil.isBlank(body)) {
            return Map.of();
        }
        JSONArray metafields = JSONUtil.parseObj(body).getJSONArray("metafields");
        if (metafields == null || metafields.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < metafields.size(); i++) {
            JSONObject mf = metafields.getJSONObject(i);
            if (mf == null) {
                continue;
            }
            String key = mf.getStr("key");
            Object value = mf.get("value");
            if (StrUtil.isNotBlank(key) && value != null) {
                result.put(key, value.toString());
            }
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
        JSONObject totalPriceSet = order.getJSONObject("current_total_price_set");
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
