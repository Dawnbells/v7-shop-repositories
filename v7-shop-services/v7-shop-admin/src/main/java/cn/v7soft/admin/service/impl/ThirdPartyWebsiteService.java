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
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(website.getToken()), String.class);

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
    public String loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo, SyncMode syncMode) {
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
        log.debug("uri = {}", uri);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(websiteDto.getToken()), String.class);
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                self.markWebsiteAuthError(request.getIdLongValue(), "Token无效或已过期 (HTTP " + statusCode + ")");
            }
            throw e;
        } catch (ResourceAccessException e) {
            log.error("连接Shopline失败: websiteId={}", request.getIdLongValue(), e);
            throw e;
        }

        if (StrUtil.isBlank(response.getBody())) {
            return null;
        }

        JSONObject body = JSONUtil.parseObj(response.getBody());
        if (body.containsKey("errors")) {
            String errors = body.get("errors", String.class);
            log.error("Shopline API返回错误: websiteId={}, errors={}", request.getIdLongValue(), errors);
            throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(request.getIdLongValue(), errors);
        }

        JSONArray orders = body.getJSONArray("orders");
        int newOrderCount = 0;
        if (orders != null && !orders.isEmpty()) {
            newOrderCount = convertAndSaveOrders(websiteDto, orders, syncMode);
        }
        if (isAutoSync) {
            self.updateLastSyncInfo(request.getIdLongValue(), orders, newOrderCount > 0);
        }

        return extractNextPageInfo(response.getHeaders());
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
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(website.getToken()), String.class);

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
    public void updateLastManualSyncTime(Long websiteId) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setLastManualSyncTime(LocalDateTime.now());
        self.saveAndFlush(website);
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
    private int convertAndSaveOrders(ThirdPartyWebsiteDto website, JSONArray orders, SyncMode syncMode) {
        SystemUserDto owner = website.getOwner();
        int newCount = 0;
        boolean updateExisting = syncMode == SyncMode.MANUAL;
        for (int i = 0; i < orders.size(); i++) {
            JSONObject order = orders.getJSONObject(i);
            try {
                if (convertShoplineOrderToTemporary(website, owner, order, updateExisting)) {
                    newCount++;
                }
            } catch (Exception e) {
                String orderId = order.getStr("id");
                if (e.getMessage() != null && e.getMessage().contains("已存在相同的原始订单ID")) {
                    log.debug("跳过已同步订单: originOrderId={}", orderId);
                } else {
                    log.error("转换Shopline订单失败: orderId={}", orderId, e);
                }
            }
        }
        return newCount;
    }

    private boolean convertShoplineOrderToTemporary(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order, boolean updateExisting) {
        CurrencyMode currencyMode = website.getCurrencyMode() != null ? website.getCurrencyMode() : CurrencyMode.SHOP_MONEY;
        String moneyKey = currencyMode == CurrencyMode.PRESENTMENT_MONEY ? "presentment_money" : "shop_money";

        log.info("=== Shopline订单转换开始 === originOrderId={}, createdAt={}, currency={}, locale={}, currencyMode={}",
                order.getStr("id"), order.getStr("created_at"), order.getStr("currency"), order.getStr("customer_locale"), currencyMode);
        if (log.isDebugEnabled()) {
            log.debug("Shopline原始订单JSON: {}", order.toStringPretty());
        }

        // 收集 line_items 中所有不重复的 Shopline product_id，批量获取 metafields
        Map<String, Map<String, String>> productMetafieldsMap = fetchMetafieldsForLineItems(
                website.getHandle(), website.getToken(), order);

        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(owner.getCompanyId());
        request.setFrom(website.getNickName() + "-SHOPLINE");
        request.setFromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""));
        request.setPlatform(WebsiteTypeEnum.SHOPLINE);
        request.setOriginOrderId(order.getStr("id"));
        request.setOrderTime(parseShoplineDateTime(order.getStr("created_at")));

        request.setDeliveryInfo(buildDeliveryInfo(order));
        request.setFinancialInfo(buildFinancialInfo(order, moneyKey));
        request.setPaymentInfo(buildPaymentInfo(order));
        request.setContextInfo(buildContextInfo(website, owner, order, moneyKey));
        request.setRiskInfo(buildRiskInfo(order));
        request.setItemInfos(buildItemInfos(order, moneyKey, owner, productMetafieldsMap));

        // 归属人优先级：归属人账号(telephone) > 归属人(name) > 第三方商城归属(website owner)
        applyOwnerFromMetafields(request.getContextInfo(), order, productMetafieldsMap);

        log.info("=== 转换后临时订单 === originOrderId={}, from={}, salesPerson={}, country={}, currency={}, "
                        + "totalAmount={}, shippingFee={}, itemCount={}, recipient={} {}, phone={}, city={}",
                request.getOriginOrderId(), request.getFrom(),
                request.getContextInfo() != null ? request.getContextInfo().getSalesPerson() : "N/A",
                request.getContextInfo() != null ? request.getContextInfo().getCountryCode() : "N/A",
                request.getContextInfo() != null ? request.getContextInfo().getCurrencyCode() : "N/A",
                request.getFinancialInfo() != null ? request.getFinancialInfo().getTotalAmount() : "N/A",
                request.getFinancialInfo() != null ? request.getFinancialInfo().getShippingFee() : "N/A",
                request.getItemInfos() != null ? request.getItemInfos().size() : 0,
                request.getDeliveryInfo() != null ? request.getDeliveryInfo().getFirstName() : "",
                request.getDeliveryInfo() != null ? request.getDeliveryInfo().getLastName() : "",
                request.getDeliveryInfo() != null ? request.getDeliveryInfo().getPhone() : "",
                request.getDeliveryInfo() != null ? request.getDeliveryInfo().getCity() : "");

        boolean created = temporaryOrderService.synchronizeOrderFromExternalSystem(request, updateExisting);
        log.info("=== Shopline订单转换完成 === originOrderId={} 已写入临时表", request.getOriginOrderId());
        return created;
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
        info.setWebsiteUrl("https://" + website.getHandle() + ".myshopline.com/admin");
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
            resolvedOwner = systemUserRepository.findByTelephoneWithDepartment(ownerTelephone.trim());
            if (resolvedOwner != null) {
                log.info("Metafield归属人匹配(telephone): telephone={}, userId={}, name={}",
                        ownerTelephone, resolvedOwner.getId(), resolvedOwner.getName());
            } else {
                log.warn("Metafield归属人账号未找到对应用户: telephone={}", ownerTelephone);
            }
        }

        if (resolvedOwner == null && StrUtil.isNotBlank(ownerName)) {
            resolvedOwner = systemUserRepository.findByUserNameWithDepartment(ownerName.trim()).orElse(null);
            if (resolvedOwner != null) {
                log.info("Metafield归属人匹配(name): name={}, userId={}", ownerName, resolvedOwner.getId());
            } else {
                log.warn("Metafield归属人未找到对应用户: name={}", ownerName);
            }
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
                String code = lineItem.getStr("sku");
                if (StrUtil.isNotBlank(code)) {
                    skuCodes.add(code.trim());
                }
            }
        }

        Map<String, ProductSKU> skuMap = new HashMap<>();
        if (!skuCodes.isEmpty()) {
            log.info("SKU查询(按部门): skuCodes={}, ownerId={}", skuCodes, owner.getLongId());
            List<ProductSKU> skuList = productSKUService.listBySkuCodes(skuCodes, owner.getLongId());
            log.info("SKU查询结果(按部门): 查询{}个, 命中{}个", skuCodes.size(), skuList.size());
            if (skuList.isEmpty()) {
                log.info("SKU按部门查询无结果，fallback按ownerId查询: ownerId={}", owner.getLongId());
                skuList = productSKUService.listBySkuCodesAndOwnerId(skuCodes, owner.getLongId());
                log.info("SKU查询结果(按ownerId): 查询{}个, 命中{}个", skuCodes.size(), skuList.size());
            }
            for (ProductSKU sku : skuList) {
                skuMap.put(sku.getSkuCode(), sku);
                log.debug("SKU映射: skuCode={} -> skuId={}, skuName={}", sku.getSkuCode(), sku.getId(), sku.getName());
            }
            if (skuList.size() < skuCodes.size()) {
                List<String> missingCodes = new ArrayList<>(skuCodes);
                missingCodes.removeAll(skuMap.keySet());
                log.warn("SKU未命中: missingSkuCodes={}", missingCodes);
            }
        } else {
            log.info("SKU查询: 订单中无有效SKU编码");
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

            String skuCode = StrUtil.blankToDefault(lineItem.getStr("sku"), "").trim();
            item.setSkuCode(skuCode);
            ProductSKU matchedSku = skuMap.get(skuCode);
            if (matchedSku != null) {
                item.setSkuId(matchedSku.getId());
                item.setSkuName(matchedSku.getName());
            } else {
                item.setSkuId(0L);
                item.setSkuName("");
            }
            log.info("SKU赋值: skuCode={}, skuId={}, skuName={}, matched={}",
                    skuCode, item.getSkuId(), item.getSkuName(), matchedSku != null);

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

    // ==================== Metafield 相关 ====================

    /**
     * 批量获取多个商品的 metafields（v7_order namespace），对 productId 去重。
     *
     * @return productId -> (metafield key -> value)
     */
    private Map<String, Map<String, String>> fetchProductMetafieldsForIds(String handle, String token, Set<String> productIds) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, fetchProductMetafields(handle, token, productId));
        }
        return result;
    }

    /**
     * 调用 Shopline GET /products/{productId}/metafields.json?namespace=v7_order 获取商品元字段。
     *
     * @return metafield key -> value 的映射；API 调用失败时返回空 Map 不中断同步
     */
    private Map<String, String> fetchProductMetafields(String handle, String token, String productId) {
        Map<String, String> result = new HashMap<>();
        try {
            String url = buildApiUrl(handle, "products/" + productId + "/metafields.json");
            URI uri = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("namespace", METAFIELD_NAMESPACE)
                    .build().toUri();
            log.debug("获取商品Metafield: productId={}, uri={}", productId, uri);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(token), String.class);
            if (StrUtil.isBlank(response.getBody())) {
                return result;
            }

            JSONObject body = JSONUtil.parseObj(response.getBody());
            JSONArray metafields = body.getJSONArray("metafields");
            if (metafields == null || metafields.isEmpty()) {
                return result;
            }

            for (int i = 0; i < metafields.size(); i++) {
                JSONObject mf = metafields.getJSONObject(i);
                if (mf == null) continue;
                String key = mf.getStr("key");
                Object value = mf.get("value");
                if (StrUtil.isNotBlank(key) && value != null) {
                    result.put(key, value.toString());
                }
            }
            log.debug("商品Metafield结果: productId={}, fields={}", productId, result.keySet());
        } catch (Exception e) {
            log.warn("获取商品Metafield失败（不影响订单同步）: productId={}, error={}", productId, e.getMessage());
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
        log.warn("商城凭证失效，已停止自动同步: websiteId={}, message={}", websiteId, message);
    }

    @Transactional
    public void updateLastSyncInfo(Long websiteId, JSONArray orders, boolean hasNewOrders) {
        LocalDateTime orderTime = null;
        String lastOrderId = null;

        if (hasNewOrders && orders != null) {
            for (int i = 0; i < orders.size(); i++) {
                JSONObject o = orders.getJSONObject(i);
                LocalDateTime createdAt = parseShoplineDateTime(o.getStr("created_at"));
                if (createdAt != null && (orderTime == null || createdAt.isAfter(orderTime))) {
                    orderTime = createdAt;
                    lastOrderId = o.getStr("id");
                }
            }
        }

        repository.updateSyncInfo(websiteId, LocalDateTime.now(), hasNewOrders, orderTime, lastOrderId);
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
            return LocalDateTime.now();
        }
        try {
            OffsetDateTime odt = OffsetDateTime.parse(dateStr, ISO_OFFSET);
            return odt.toLocalDateTime();
        } catch (Exception e) {
            log.warn("解析Shopline时间失败: {}", dateStr);
            return LocalDateTime.now();
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
