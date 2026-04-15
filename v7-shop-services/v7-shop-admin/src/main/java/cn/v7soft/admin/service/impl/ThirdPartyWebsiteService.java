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
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.LocalDateTimeUtils;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.Country;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.Language;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
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
    private static final String API_VERSION = "v20250601";
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
                                    ITemporaryOrderService temporaryOrderService) {
        super(repository);
        this.restTemplate = restTemplate;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskExecutorService = taskExecutorService;
        this.currencyService = currencyService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.temporaryOrderService = temporaryOrderService;
    }

    // ==================== 公开接口 ====================

    @Override
    public Optional<ThirdPartyWebsite> getByToken(String token) {
        return repository.findByToken(token);
    }

    @Override
    public Optional<ThirdPartyWebsite> getByAppKeyAndAuthType(String appKey, ThirdPartyAuthTypeEnum authType) {
        return repository.findByAppKeyAndAuthType(appKey, authType);
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
    public String loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo, boolean updateSyncTime) {
        ThirdPartyWebsiteDto websiteDto = self.getThirdPartyWebsiteDtoById(request.getIdLongValue());
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(websiteDto.getToken(), request.getId());

        String url = buildApiUrl(websiteDto.getHandle(), "orders.json");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        if (StrUtil.isNotBlank(pageInfo)) {
            builder.queryParam("page_info", pageInfo);
        } else {
            if (request.getCreateAtMin() != null) {
                builder.queryParam("created_at_min", LocalDateTimeUtils.formatZone8(request.getCreateAtMin()));
            }
            if (request.getCreateAtMax() != null) {
                builder.queryParam("created_at_max", LocalDateTimeUtils.formatZone8(request.getCreateAtMax()));
            }
        }
        builder.queryParam("limit", "100");
        URI uri = builder.build().toUri();

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.GET, buildHttpEntity(websiteDto.getToken()), String.class);
        } catch (HttpClientErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                markWebsiteAuthError(request.getIdLongValue(), "Token无效或已过期 (HTTP " + statusCode + ")");
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
        if (orders != null && !orders.isEmpty()) {
            convertAndSaveOrders(websiteDto, orders);
            if (updateSyncTime) {
                updateLastSyncTime(request.getIdLongValue(), orders);
            }
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
    public List<ThirdPartyWebsite> findSyncEnabledWebsites() {
        return repository.findBySyncEnabledTrueAndAuthStatus(ThirdPartyAuthStatusEnum.AUTHED);
    }

    @Override
    public void updateLastManualSyncTime(Long websiteId) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setLastManualSyncTime(LocalDateTime.now());
        saveAndFlush(website);
    }

    @Override
    protected void checkKeyConstraint(ThirdPartyWebsite data) {
    }

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

    private void convertAndSaveOrders(ThirdPartyWebsiteDto website, JSONArray orders) {
        SystemUserDto owner = website.getOwner();
        for (int i = 0; i < orders.size(); i++) {
            JSONObject order = orders.getJSONObject(i);
            try {
                convertShoplineOrderToTemporary(website, owner, order);
            } catch (Exception e) {
                String orderId = order.getStr("id");
                if (e.getMessage() != null && e.getMessage().contains("已存在相同的原始订单ID")) {
                    log.debug("跳过已同步订单: originOrderId={}", orderId);
                } else {
                    log.error("转换Shopline订单失败: orderId={}", orderId, e);
                }
            }
        }
    }

    private void convertShoplineOrderToTemporary(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order) {
        log.info("=== Shopline订单转换开始 === originOrderId={}, createdAt={}, currency={}, locale={}",
                order.getStr("id"), order.getStr("created_at"), order.getStr("currency"), order.getStr("customer_locale"));
        if (log.isDebugEnabled()) {
            log.debug("Shopline原始订单JSON: {}", order.toStringPretty());
        }

        EditTemporaryOrderRequest request = new EditTemporaryOrderRequest();
        request.setCompanyId(owner.getCompanyId());
        request.setFrom(website.getNickName());
        request.setFromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""));
        request.setPlatform(WebsiteTypeEnum.SHOPLINE);
        request.setOriginOrderId(order.getStr("id"));
        request.setOrderTime(parseShoplineDateTime(order.getStr("created_at")));

        request.setDeliveryInfo(buildDeliveryInfo(order));
        request.setFinancialInfo(buildFinancialInfo(order));
        request.setPaymentInfo(buildPaymentInfo(order));
        request.setContextInfo(buildContextInfo(website, owner, order));
        request.setRiskInfo(buildRiskInfo(order));
        request.setItemInfos(buildItemInfos(order));

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

        temporaryOrderService.synchronizeOrderFromExternalSystem(request);
        log.info("=== Shopline订单转换完成 === originOrderId={} 已写入临时表", request.getOriginOrderId());
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
            info.setDistrict(StrUtil.blankToDefault(addr.getStr("address2"), ""));
            info.setPostalCode(StrUtil.blankToDefault(addr.getStr("zip"), ""));
            info.setAddress(StrUtil.blankToDefault(addr.getStr("address1"), ""));
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

    private TemporaryOrderFinancialInfoRequest buildFinancialInfo(JSONObject order) {
        TemporaryOrderFinancialInfoRequest info = new TemporaryOrderFinancialInfoRequest();
        info.setTotalAmount(parseBigDecimal(order.getStr("current_total_price")));
        info.setDiscountAmount(parseBigDecimal(order.getStr("current_total_discounts")));
        info.setTaxAmount(parseBigDecimal(order.getStr("current_total_tax")));

        BigDecimal shippingFee = BigDecimal.ZERO;
        JSONArray shippingLines = order.getJSONArray("shipping_lines");
        if (shippingLines != null) {
            for (int i = 0; i < shippingLines.size(); i++) {
                JSONObject line = shippingLines.getJSONObject(i);
                if (line != null) {
                    String price = line.getStr("price");
                    if (price != null && !"0.00".equals(price)) {
                        shippingFee = shippingFee.add(new BigDecimal(price));
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

    private TemporaryOrderContextInfoRequest buildContextInfo(ThirdPartyWebsiteDto website, SystemUserDto owner, JSONObject order) {
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

        String currencyCode = order.getStr("currency");
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
                String countryCode = matcher.group(2);

                Optional<Language> langOpt = languageService.getByCode(langCode);
                if (langOpt.isPresent()) {
                    Language language = langOpt.get();
                    info.setLanguageId(String.valueOf(language.getId()));
                    info.setLanguage(language.getName());
                    info.setLanguageCode(language.getCode());
                } else {
                    info.setLanguageCode(langCode.toUpperCase());
                }

                Optional<Country> countryOpt = countryService.getByCode(countryCode);
                if (countryOpt.isPresent()) {
                    Country country = countryOpt.get();
                    info.setCountryId(country.getId());
                    info.setCountry(country.getName());
                    info.setCountryCode(country.getCode());
                } else {
                    info.setCountryCode(countryCode.toUpperCase());
                }
            }
        }

        JSONObject shippingAddress = order.getJSONObject("shipping_address");
        if (shippingAddress != null && info.getCountryCode() == null) {
            String countryCode = shippingAddress.getStr("country_code");
            if (StrUtil.isNotBlank(countryCode)) {
                Optional<Country> countryOpt = countryService.getByCode(countryCode.trim().toUpperCase());
                if (countryOpt.isPresent()) {
                    Country country = countryOpt.get();
                    info.setCountryId(country.getId());
                    info.setCountry(country.getName());
                    info.setCountryCode(country.getCode());
                } else {
                    info.setCountryCode(countryCode.trim().toUpperCase());
                }
            }
        }

        return info;
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

    private List<TemporaryOrderItemInfoRequest> buildItemInfos(JSONObject order) {
        JSONArray lineItems = order.getJSONArray("line_items");
        if (lineItems == null || lineItems.isEmpty()) {
            return List.of();
        }
        List<TemporaryOrderItemInfoRequest> items = new ArrayList<>(lineItems.size());
        for (int i = 0; i < lineItems.size(); i++) {
            JSONObject lineItem = lineItems.getJSONObject(i);
            if (lineItem == null) continue;

            TemporaryOrderItemInfoRequest item = new TemporaryOrderItemInfoRequest();
            item.setSpuId("0");
            item.setProductId("0");
            item.setTitle(StrUtil.blankToDefault(lineItem.getStr("title"), ""));
            item.setSpecTitle(StrUtil.blankToDefault(lineItem.getStr("attribute"), ""));
            item.setSellPrice(parseBigDecimal(lineItem.getStr("price")));
            item.setOriginPrice(BigDecimal.ZERO);
            item.setCostPrice(BigDecimal.ZERO);
            item.setTax(BigDecimal.ZERO);
            item.setBarcode("");
            item.setQuantity(Integer.parseInt(StrUtil.blankToDefault(lineItem.getStr("quantity"), "0")));
            item.setSkuName(StrUtil.blankToDefault(lineItem.getStr("sku"), ""));
            item.setSkuCode(StrUtil.blankToDefault(lineItem.getStr("variant_id"), ""));
            item.setSkuIsVirtual(false);
            item.setMerchandise("");
            items.add(item);
        }
        return items;
    }

    // ==================== 工具方法 ====================

    @Transactional
    public void markWebsiteAuthError(Long websiteId, String message) {
        ThirdPartyWebsite website = getById(websiteId);
        website.setAuthStatus(ThirdPartyAuthStatusEnum.ERROR);
        website.setAuthMessage(message);
        website.setSyncEnabled(false);
        saveAndFlush(website);
        log.warn("商城凭证失效，已停止自动同步: websiteId={}, message={}", websiteId, message);
    }

    private void updateLastSyncTime(Long websiteId, JSONArray orders) {
        LocalDateTime maxTime = null;
        for (int i = 0; i < orders.size(); i++) {
            JSONObject o = orders.getJSONObject(i);
            LocalDateTime createdAt = parseShoplineDateTime(o.getStr("created_at"));
            if (createdAt != null && (maxTime == null || createdAt.isAfter(maxTime))) {
                maxTime = createdAt;
            }
        }
        if (maxTime != null) {
            ThirdPartyWebsite website = getById(websiteId);
            website.setLastSyncTime(maxTime);
            saveAndFlush(website);
        }
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
