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
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.SyncMode;
import cn.v7soft.admin.utils.OrderQueryHelper;
import cn.v7soft.admin.service.dto.ShoplineOrderLoadResult;
import cn.v7soft.dao.entities.primary.ProductSKU;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.LocalDateTimeUtils;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ThirdPartyWebsiteService extends BaseDataRangeService<ThirdPartyWebsite, ThirdPartyWebsiteRepository> implements IThirdPartyWebsiteService {
    private static final String API_VERSION = "v20260901";
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

    private final RestTemplate restTemplate;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskExecutorService taskExecutorService;
    private final ICurrencyService currencyService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final ITemporaryOrderService temporaryOrderService;
    private final IProductSKUService productSKUService;
    private final SystemUserRepository systemUserRepository;

    @Autowired
    @Lazy
    private ThirdPartyWebsiteService self;

    public ThirdPartyWebsiteService(ThirdPartyWebsiteRepository repository,
                                    RestTemplate restTemplate,
                                    AsyncTaskRepository asyncTaskRepository,
                                    ITaskExecutorService taskExecutorService,
                                    ICurrencyService currencyService,
                                    ILanguageService languageService,
                                    ICountryService countryService,
                                    ITemporaryOrderService temporaryOrderService,
                                    IProductSKUService productSKUService,
                                    SystemUserRepository systemUserRepository) {
        super(repository);
        this.restTemplate = restTemplate;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskExecutorService = taskExecutorService;
        this.currencyService = currencyService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.temporaryOrderService = temporaryOrderService;
        this.productSKUService = productSKUService;
        this.systemUserRepository = systemUserRepository;
    }

    // ==================== 公开接口 ====================

    @Override
    public Optional<ThirdPartyWebsite> getByToken(String token) {
        return repository.findByToken(token);
    }

    @Override
    public Optional<ThirdPartyWebsite> getByHandle(String handle) {
        return repository.findByHandle(handle);
    }

    @Override
    public CountThirdPartyOrderResponse countOrders(CountThirdPartyOrdersRequest request) {
        ThirdPartyWebsite website = getById(request.getIdLongValue());
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(website.getToken(), request.getId());

        String url = buildApiUrl(website.getHandle(), "orders/count.json");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        if (request.getCreateAtMin() != null) {
            builder.queryParam("created_at_min", LocalDateTimeUtils.formatZone8(request.getCreateAtMin()));
        }
        if (request.getCreateAtMax() != null) {
            builder.queryParam("created_at_max", LocalDateTimeUtils.formatZone8(request.getCreateAtMax()));
        }
        URI uri = builder.build().toUri();

        ResponseEntity<String> response = callShoplineApi(website.getHandle(),
                () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(website.getToken()), String.class));

        String errors = "status: " + response.getStatusCode();
        if (StrUtil.isNotBlank(response.getBody())) {
            JSONObject body = JSONUtil.parseObj(response.getBody());
            if (body.containsKey("count")) {
                return CountThirdPartyOrderResponse.builder()
                        .count(body.get("count", Integer.class))
                        .build();
            }
            if (body.containsKey("errors")) {
                errors = body.get("errors", String.class);
            }
        }
        throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(request.getIdLongValue(), errors);
    }

    /**
     * 拉取订单并写入临时表，返回下一页的 page_info（null 表示没有更多页）
     */
    @Override
    public ShoplineOrderLoadResult loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo, SyncMode syncMode) {
        boolean isAutoSync = syncMode == SyncMode.AUTO;
        ThirdPartyWebsiteDto websiteDto = self.getThirdPartyWebsiteDtoById(request.getIdLongValue());
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(websiteDto.getToken(), request.getId());

        String url = buildApiUrl(websiteDto.getHandle(), "orders.json");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        if (StrUtil.isNotBlank(pageInfo)) {
            builder.queryParam("page_info", pageInfo);
        } else {
            builder.queryParam("sort_condition", "order_at:asc,id:asc");
            if (request.getCreateAtMin() != null) {
                builder.queryParam("created_at_min", LocalDateTimeUtils.formatZone8(request.getCreateAtMin()));
            }
            if (request.getCreateAtMax() != null) {
                builder.queryParam("created_at_max", LocalDateTimeUtils.formatZone8(request.getCreateAtMax()));
            }
            if (isAutoSync && StrUtil.isNotBlank(websiteDto.getLastSyncOrderId())) {
                builder.queryParam("since_id", websiteDto.getLastSyncOrderId());
            }
        }
        builder.queryParam("limit", "100");
        URI uri = builder.build().toUri();
        ResponseEntity<String> response;
        try {
            response = callShoplineApi(websiteDto.getHandle(),
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(websiteDto.getToken()), String.class));
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                self.markWebsiteAuthError(request.getIdLongValue(), "Token无效或已过期 (HTTP " + statusCode + ")");
            }
            log.error("Shopline order sync page request failed: websiteId={}, handle={}, syncMode={}, status={}, uri={}",
                    websiteDto.getId(), websiteDto.getHandle(), syncMode, statusCode, uri, e);
            throw e;
        }

        if (StrUtil.isBlank(response.getBody())) {
            return ShoplineOrderLoadResult.empty(null);
        }

        JSONObject body = JSONUtil.parseObj(response.getBody());
        if (body.containsKey("errors")) {
            String errors = body.get("errors", String.class);
            throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(request.getIdLongValue(), errors);
        }

        JSONArray orders = body.getJSONArray("orders");
        String nextPageInfoFromHeader = extractNextPageInfo(response.getHeaders());
        ShoplineOrderLoadResult pageResult = ShoplineOrderLoadResult.empty(nextPageInfoFromHeader);
        if (orders != null && !orders.isEmpty()) {
            pageResult = convertAndSaveOrders(websiteDto, orders, syncMode, nextPageInfoFromHeader);
        }
        if (isAutoSync) {
            self.updateLastSyncInfo(request.getIdLongValue(), pageResult);
        }

        return pageResult;
    }

    @Override
    @Transactional
    public Long submitSyncOrders(SyncThirdPartyOrdersRequest request) {
        AsyncTask asyncTask = AsyncTask.builder()
                .taskType(TaskType.THIRD_PARTY_ORDER_SYNC)
                .state(TaskState.PENDING)
                .progress(0)
                .parameters(JSONUtil.toJsonStr(request))
                .build()
                .fillOwner();
        asyncTask = asyncTaskRepository.saveAndFlush(asyncTask);
        taskExecutorService.submitAsyncTask(asyncTask.getId());
        return asyncTask.getId();
    }

    /**
     * 验证 Shopline Token 有效性，更新 authStatus 和 authMessage
     */
    @Override
    @Transactional
    public void verifyAndUpdateAuthStatus(ThirdPartyWebsite website) {
        if (website.getWebsiteType() != WebsiteTypeEnum.SHOPLINE) {
            return;
        }
        try {
            String url = buildApiUrl(website.getHandle(), "orders/count.json");
            URI uri = UriComponentsBuilder.fromHttpUrl(url).build().toUri();
            ResponseEntity<String> response = callShoplineApi(website.getHandle(),
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(website.getToken()), String.class));

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
            if (code == 401 || code == 403) {
                website.setAuthMessage("Token无效或已过期");
            } else {
                website.setAuthMessage("API错误: HTTP " + code);
            }
        } catch (ResourceAccessException e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            website.setAuthMessage("无法连接到Shopline，请检查Handle是否正确");
        } catch (Exception e) {
            website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
            website.setAuthMessage("验证失败: " + e.getMessage());
        }
    }

    @Override
    public List<ThirdPartyWebsite> findActiveWebsites() {
        return repository.findByStatusAndAuthStatus(StatusEnum.VALID, ThirdPartyAuthStatusEnum.AUTHED);
    }

    @Override
    @Transactional
    public void updateLastManualSyncTime(Long websiteId) {
        repository.updateLastManualSyncTime(websiteId, LocalDateTime.now());
    }


    @Override
    @Transactional
    public ThirdPartyWebsiteDto getThirdPartyWebsiteDtoById(Long id) {
        ThirdPartyWebsite website = getById(id);
        return ThirdPartyWebsiteDto.convert(website);
    }

    // ==================== 内部方法 ====================

    private String buildApiUrl(String handle, String endpoint) {
        return "https://" + handle + ".myshopline.com/admin/openapi/" + API_VERSION + "/" + endpoint;
    }

    private HttpEntity<String> buildHttpEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + token);
        return new HttpEntity<>(headers);
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

    /**
     * @return 实际新增的订单数（已存在的重复订单不计入）
     */
    private ShoplineOrderLoadResult convertAndSaveOrders(ThirdPartyWebsiteDto website, JSONArray orders, SyncMode syncMode, String nextPageInfo) {
        SystemUserDto owner = website.getOwner();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int createdCount = 0;
        boolean updateExisting = syncMode == SyncMode.MANUAL;

        // 保守策略游标：仅记录已成功处理（created/skipped）的最大 id；
        // 一旦遇到失败单立即终止本页处理，剩余订单留待下一轮重新拉取，
        // 既能避免失败单被游标跳过造成永久漏单，也能避免系统性故障下的连锁失败。
        String cursorOrderId = null;
        LocalDateTime cursorOrderTime = null;
        boolean abortedByFailure = false;
        int processedIndex = 0;

        for (int i = 0; i < orders.size(); i++) {
            JSONObject order = orders.getJSONObject(i);
            String originOrderId = order.getStr("id");
            try {
                if (!updateExisting && StrUtil.isNotBlank(originOrderId)
                        && temporaryOrderService.findByOriginOrderId(originOrderId).isPresent()) {
                    skippedCount++;
                } else {
                    if (convertShoplineOrderToTemporary(website, owner, order, updateExisting)) {
                        createdCount++;
                    }
                    successCount++;
                }
            } catch (Exception e) {
                failedCount++;
                abortedByFailure = true;
                log.error("Shopline order sync failed: websiteId={}, handle={}, syncMode={}, orderIndex={}/{}, orderId={}, orderName={}, createdAt={}, financialStatus={}, fulfillmentStatus={}",
                        website.getId(), website.getHandle(), syncMode, i, orders.size(), order.getStr("id"), order.getStr("name"),
                        order.getStr("created_at"), order.getStr("financial_status"), order.getStr("fulfillment_status"), e);
                break;
            }
            processedIndex = i + 1;

            if (originOrderId != null && (cursorOrderId == null || compareShoplineOrderId(originOrderId, cursorOrderId) > 0)) {
                cursorOrderId = originOrderId;
            }
            LocalDateTime createdAt = parseShoplineDateTime(order.getStr("created_at"));
            if (createdAt != null && (cursorOrderTime == null || createdAt.isAfter(cursorOrderTime))) {
                cursorOrderTime = createdAt;
            }
        }

        if (abortedByFailure) {
            int remaining = orders.size() - processedIndex - 1;
            log.warn("Shopline order sync aborted by failure: websiteId={}, handle={}, syncMode={}, created={}, skipped={}, failed={}, remainingForNextRound={}, cursorOrderId={}",
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
    private boolean convertShoplineOrderToTemporary(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order, boolean updateExisting) {
        CurrencyMode currencyMode = website.getCurrencyMode() != null ? website.getCurrencyMode() : CurrencyMode.SHOP_MONEY;
        String moneyKey = currencyMode == CurrencyMode.PRESENTMENT_MONEY ? "presentment_money" : "shop_money";

        // 收集 line_items 中所有不重复的 Shopline product_id，批量获取 metafields
        Map<String, Map<String, String>> productMetafieldsMap = fetchMetafieldsForLineItems(
                website.getHandle(), website.getToken(), order);

        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(owner.getCompanyId());
        request.setFrom(website.getNickName() + "-SHOPLINE");
        request.setFromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""));
        request.setPlatform(WebsiteTypeEnum.SHOPLINE);
        request.setOriginOrderId(order.getStr("id"));
        LocalDateTime orderTime = parseShoplineDateTime(order.getStr("created_at"));
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
        info.setRemark(StrUtil.blankToDefault(order.getStr("buyer_note"), ""));

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
        } else {
            JSONObject customer = order.getJSONObject("customer");
            if (customer != null) {
                info.setFirstName(StrUtil.blankToDefault(customer.getStr("first_name"), ""));
                info.setLastName(StrUtil.blankToDefault(customer.getStr("last_name"), ""));
                info.setPhone(StrUtil.blankToDefault(customer.getStr("phone"), ""));
            }
        }

        JSONObject customer = order.getJSONObject("customer");
        if (customer != null) {
            info.setEmail(StrUtil.blankToDefault(customer.getStr("email"), ""));
        }
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
                if (line != null) {
                    JSONObject priceSet = line.getJSONObject("price_set");
                    if (priceSet != null) {
                        shippingFee = shippingFee.add(extractAmountFromMoneySet(priceSet, moneyKey));
                    } else {
                        String price = line.getStr("price");
                        if (price != null && !"0.00".equals(price)) {
                            shippingFee = shippingFee.add(new BigDecimal(price));
                        }
                    }
                }
            }
        }
        info.setShippingFee(shippingFee);
        return info;
    }

    private TemporaryOrderPaymentInfoRequest buildPaymentInfo(JSONObject order) {
        TemporaryOrderPaymentInfoRequest info = new TemporaryOrderPaymentInfoRequest();
        info.setPaymentMethod(PaymentMethod.COD);
        info.setPaymentStatus(PaymentStatus.convertFromShopline(order.getStr("financial_status")));
        info.setPaymentTime(LocalDateTime.now());
        return info;
    }

    private TemporaryOrderContextInfoRequest buildContextInfo(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order, String moneyKey) {
        TemporaryOrderContextInfoRequest info = new TemporaryOrderContextInfoRequest();
        info.setSalesUid(owner.getLongId());
        info.setSalesPerson(owner.getName());
        info.setDepartmentId(owner.getDepartmentId());
        info.setDepartment(StrUtil.blankToDefault(owner.getDepartmentName(), ""));
        info.setWebsiteId(website.getLongId());
        info.setWebsiteName(website.getNickName());
        info.setWebsiteUrl(OrderQueryHelper.extractHost("https://" + website.getHandle() + ".myshopline.com/admin"));
        info.setAddressRule("");
        info.setPhoneRule("");

        String currencyCode = extractCurrencyCode(order, moneyKey);
        if (StrUtil.isNotBlank(currencyCode)) {
            Optional<Currency> currencyOpt = currencyService.getByCode(currencyCode.trim().toUpperCase());
            if (currencyOpt.isPresent()) {
                Currency currency = currencyOpt.get();
                info.setCurrencyId(currency.getId());
                info.setCurrencyCode(currency.getCode());
                info.setCurrencySymbol(currency.getSymbol());
                info.setCurrencyName(currency.getName());
                info.setCurrencyFractionDigits(currency.getFractionDigits());
                info.setCurrencyExchangeRate(currency.getExchangeRate());
            } else {
                info.setCurrencyCode(currencyCode.trim().toUpperCase());
            }
        }

        String customerLocale = order.getStr("customer_locale");
        if (StrUtil.isNotBlank(customerLocale)) {
            Matcher matcher = LOCALE_PATTERN.matcher(customerLocale);
            if (matcher.find()) {
                String langCode = matcher.group(1);
                String localeCountryCode = matcher.group(2);

                Optional<Language> langOpt = languageService.getByCode(langCode);
                if (langOpt.isPresent()) {
                    Language language = langOpt.get();
                    info.setLanguageId(String.valueOf(language.getId()));
                    info.setLanguage(language.getName());
                    info.setLanguageCode(language.getCode());
                } else {
                    info.setLanguageCode(langCode.toUpperCase());
                }

                resolveCountry(info, localeCountryCode);
            } else {
                Optional<Language> langOpt = languageService.getByCode(customerLocale.trim().toLowerCase());
                if (langOpt.isPresent()) {
                    Language language = langOpt.get();
                    info.setLanguageId(String.valueOf(language.getId()));
                    info.setLanguage(language.getName());
                    info.setLanguageCode(language.getCode());
                }
            }
        }

        JSONObject shippingAddress = order.getJSONObject("shipping_address");
        if (shippingAddress != null) {
            String shippingCountryCode = shippingAddress.getStr("country_code");
            if (StrUtil.isNotBlank(shippingCountryCode) && info.getCountryId() == null) {
                resolveCountry(info, shippingCountryCode);
            }
        }

        JSONObject billingAddress = order.getJSONObject("billing_address");
        if (billingAddress != null && info.getCountryId() == null) {
            String billingCountryCode = billingAddress.getStr("country_code");
            if (StrUtil.isNotBlank(billingCountryCode)) {
                resolveCountry(info, billingCountryCode);
            }
        }

        return info;
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
        info.setBrowserPlatform(BrowserPlatform.fromUaStr(ua));
        return info;
    }

    /**
     * 收集订单 line_items 中所有不重复的 Shopline product_id，批量获取 metafields。
     */
    private Map<String, Map<String, String>> fetchMetafieldsForLineItems(String handle, String token, JSONObject order) {
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
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return fetchProductMetafieldsForIds(handle, token, productIds);
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
        // 取第一个有效 line_item 的 product_id 对应的 metafields
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return;
        }
        Map<String, String> firstMetafields = null;
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem == null) continue;
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
            item.setSpecTitle(StrUtil.blankToDefault(lineItem.getStr("attribute"), ""));
            item.setImage(StrUtil.blankToDefault(lineItem.getStr("image_url"), ""));

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

            String defaultMerchandise = StrUtil.blankToDefault(lineItem.getStr("title"), "");
            item.setMerchandise(defaultMerchandise);

            String shoplineProductId = lineItem.getStr("product_id");
            if (StrUtil.isNotBlank(shoplineProductId)) {
                Map<String, String> metafields = productMetafieldsMap.getOrDefault(shoplineProductId, Map.of());
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

    private String resolveSkuCode(JSONObject lineItem, Map<String, Map<String, String>> productMetafieldsMap) {
        String skuCode = StrUtil.blankToDefault(lineItem.getStr("sku"), "").trim();
        String shoplineProductId = lineItem.getStr("product_id");
        if (StrUtil.isBlank(shoplineProductId)) {
            return skuCode;
        }
        Map<String, String> metafields = productMetafieldsMap.getOrDefault(shoplineProductId, Map.of());
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

    // ==================== Shopline API 限流 & 重试 ====================

    private static final int SHOPLINE_RATE_LIMIT_PER_SECOND = 4;
    private static final int SHOPLINE_MAX_RETRIES = 3;
    private static final Duration SHOPLINE_RETRY_WAIT = Duration.ofSeconds(1);

    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    private final Retry shoplineRetry = Retry.of("shopline-api", RetryConfig.custom()
            .maxAttempts(SHOPLINE_MAX_RETRIES)
            .waitDuration(SHOPLINE_RETRY_WAIT)
            .retryOnException(e -> e instanceof HttpClientErrorException.TooManyRequests
                    || e instanceof ResourceAccessException)
            .build());

    private RateLimiter getRateLimiter(String handle) {
        return rateLimiters.computeIfAbsent(handle, h -> RateLimiter.of("shopline-" + h, RateLimiterConfig.custom()
                .limitForPeriod(SHOPLINE_RATE_LIMIT_PER_SECOND)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build()));
    }

    /**
     * 统一包裹 Shopline API 调用：限流 + 429 重试。
     */
    private <T> T callShoplineApi(String handle, Supplier<T> apiCall) {
        RateLimiter limiter = getRateLimiter(handle);
        Supplier<T> decorated = Retry.decorateSupplier(shoplineRetry, RateLimiter.decorateSupplier(limiter, apiCall));
        return decorated.get();
    }

    // ==================== Metafield 相关 ====================

    /**
     * 批量获取多个商品的 metafields，对 productId 去重。
     */
    private Map<String, Map<String, String>> fetchProductMetafieldsForIds(String handle, String token, Set<String> productIds) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, fetchProductMetafields(handle, token, productId));
        }
        return result;
    }

    /**
     * 获取单个商品的 metafields，受限流 + 重试保护。失败时降级返回空 Map。
     */
    private Map<String, String> fetchProductMetafields(String handle, String token, String productId) {
        String url = buildApiUrl(handle, "products/" + productId + "/metafields.json");
        URI uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("namespace", METAFIELD_NAMESPACE)
                .build().toUri();
        try {
            ResponseEntity<String> response = callShoplineApi(handle,
                    () -> restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(token), String.class));
            return parseMetafieldResponse(response.getBody());
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Shopline fetchMetafields 429 exhausted retries: productId={}", productId);
        } catch (HttpClientErrorException e) {
            log.warn("Shopline fetchMetafields HTTP error: productId={}, status={}", productId, e.getStatusCode());
        } catch (Exception e) {
            log.warn("Shopline fetchMetafields failed: productId={}, error={}", productId, e.getMessage());
        }
        return Map.of();
    }

    private Map<String, String> parseMetafieldResponse(String body) {
        if (StrUtil.isBlank(body)) {
            return Map.of();
        }
        JSONObject json = JSONUtil.parseObj(body);
        JSONArray metafields = json.getJSONArray("metafields");
        if (metafields == null || metafields.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < metafields.size(); i++) {
            JSONObject mf = metafields.getJSONObject(i);
            if (mf == null) continue;
            String key = mf.getStr("key");
            Object value = mf.get("value");
            if (StrUtil.isNotBlank(key) && value != null) {
                result.put(key, value.toString());
            }
        }
        return result;
    }

    // ==================== 工具方法 ====================

    @Transactional
    public void markWebsiteAuthError(Long websiteId, String message) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
        website.setAuthMessage(message);
        website.setStatus(StatusEnum.INVALID);
        saveAndFlush(website);
    }

    @Transactional
    public void updateLastSyncInfo(Long websiteId, ShoplineOrderLoadResult result) {
        boolean hasNewOrders = result != null && result.getCreatedCount() > 0;
        LocalDateTime orderTime = result != null ? result.getCursorOrderTime() : null;
        String lastOrderId = result != null ? result.getCursorOrderId() : null;
        repository.updateSyncInfo(websiteId, LocalDateTime.now(), hasNewOrders, orderTime, lastOrderId);
    }

    private static int compareShoplineOrderId(String a, String b) {
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

    /**
     * 从 money_set JSON（含 shop_money / presentment_money）中提取 amount
     */
    private BigDecimal extractAmountFromMoneySet(JSONObject moneySet, String moneyKey) {
        JSONObject money = moneySet.getJSONObject(moneyKey);
        if (money != null) {
            return parseBigDecimal(money.getStr("amount"));
        }
        return BigDecimal.ZERO;
    }

    /**
     * 根据 moneyKey 从订单的 total_price_set 中提取 currency_code，fallback 到顶层 currency
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
        return order.getStr("currency");
    }

    private LocalDateTime parseShoplineDateTime(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return null;
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(dateStr, ISO_OFFSET);
            return odt.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Shopline datetime: {}", dateStr, e);
            return null;
        }
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
