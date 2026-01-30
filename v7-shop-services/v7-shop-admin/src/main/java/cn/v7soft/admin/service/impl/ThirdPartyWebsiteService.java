package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.controller.req.CountThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.req.SyncThirdPartyOrdersRequest;
import cn.v7soft.admin.controller.resp.CountThirdPartyOrderResponse;
import cn.v7soft.admin.service.*;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.common.service.impl.BaseDataRangeService;
import cn.v7soft.common.utils.LocalDateTimeUtils;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.meta.OrderDeliveryInfo;
import cn.v7soft.dao.entities.meta.OrderFinancialInfo;
import cn.v7soft.dao.entities.meta.OrderPaymentInfo;
import cn.v7soft.dao.entities.primary.AsyncTask;
import cn.v7soft.dao.entities.primary.OrderBotCheckInfo;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.OrderLogisticsInfo;
import cn.v7soft.dao.entities.primary.Order;
import cn.v7soft.dao.entities.primary.OrderItemInfo;
import cn.v7soft.dao.entities.primary.OrderRiskRecordInfo;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.*;
import cn.v7soft.dao.repositories.primary.AsyncTaskRepository;
import cn.v7soft.dao.repositories.primary.BotOrderCheckInfoRepository;
import cn.v7soft.dao.repositories.primary.LogisticsInfoRepository;
import cn.v7soft.dao.repositories.primary.ThirdPartyWebsiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ThirdPartyWebsiteService extends BaseDataRangeService<ThirdPartyWebsite, ThirdPartyWebsiteRepository> implements IThirdPartyWebsiteService {
    private final RestTemplate restTemplate;
    private final AsyncTaskRepository asyncTaskRepository;
    private final ITaskService taskService;
    private final ICurrencyService currencyService;
    private final ILanguageService languageService;
    private final ICountryService countryService;
    private final BotOrderCheckInfoRepository botOrderCheckInfoRepository;
    private final LogisticsInfoRepository logisticsInfoRepository;
    private final OrderService orderService;
    @Autowired
    @Lazy
    private ThirdPartyWebsiteService thirdPartyWebsiteService;

    public ThirdPartyWebsiteService(ThirdPartyWebsiteRepository repository, RestTemplate restTemplate, AsyncTaskRepository asyncTaskRepository, ITaskService taskService, ICurrencyService currencyService, ILanguageService languageService, ICountryService countryService, BotOrderCheckInfoRepository botOrderCheckInfoRepository, LogisticsInfoRepository logisticsInfoRepository, OrderService orderService) {
        super(repository);
        this.restTemplate = restTemplate;
        this.asyncTaskRepository = asyncTaskRepository;
        this.taskService = taskService;
        this.currencyService = currencyService;
        this.languageService = languageService;
        this.countryService = countryService;
        this.botOrderCheckInfoRepository = botOrderCheckInfoRepository;
        this.logisticsInfoRepository = logisticsInfoRepository;
        this.orderService = orderService;
    }

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
        ThirdPartyWebsite thirdPartyWebsite = getById(request.getIdLongValue());
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(thirdPartyWebsite.getToken(), request.getId());
        // 请求 URL
        final String URL = "https://" + thirdPartyWebsite.getHandle() + ".myshopline.com/admin/openapi/v20250601/orders/count.json";
        // 构建 URL，并添加参数
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(URL);
        if (request.getCreateAtMin() != null) {
            uriComponentsBuilder.queryParam("created_at_min", LocalDateTimeUtils.formatZone8(request.getCreateAtMin()));
        }
        if (request.getCreateAtMax() != null) {
            uriComponentsBuilder.queryParam("created_at_max", LocalDateTimeUtils.formatZone8(request.getCreateAtMax()));
        }
        if (request.hasId()) {
            uriComponentsBuilder.queryParam("since_id", request.getIdLongValue());
        }
        URI uri = uriComponentsBuilder.build().toUri();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer  " + thirdPartyWebsite.getToken());

        // 由于 GET 请求不带请求体，所以 `HttpEntity` 只包含 headers
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 发送 GET 请求
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

        String errors = "status: " + response.getStatusCode();
        if (StrUtil.isNotBlank(response.getBody())) {
            JSONObject entries = JSONUtil.parseObj(response.getBody());
            if (entries.containsKey("count")) {
                Integer count = entries.get("count", Integer.class);
                return CountThirdPartyOrderResponse.builder()
                        .count(count)
                        .build();
            }
            if (entries.containsKey("errors")) {
                errors = entries.get("errors", String.class);
            }
        }
        // 返回响应体
        throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(request.getIdLongValue(), errors);
    }
    private String sliceId;
    @Override
    public void loadOrders(SyncThirdPartyOrdersRequest request, String pageInfo) {
        ThirdPartyWebsiteDto thirdPartyWebsite = thirdPartyWebsiteService.getThirdPartyWebsiteDtoById(request.getIdLongValue());
        ServiceResponseEnum.ERR_TOKEN_EMPTY.notBlank(thirdPartyWebsite.getToken(), request.getId());
        // 请求 URL
        final String URL = "https://" + thirdPartyWebsite.getHandle() + ".myshopline.com/admin/openapi/v20260301/orders.json";
        // 构建 URL，并添加参数
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(URL);
        if (request.getCreateAtMin() != null) {
            uriComponentsBuilder.queryParam("created_at_min", LocalDateTimeUtils.formatZone8(request.getCreateAtMin()));
        }
        if (request.getCreateAtMax() != null) {
            uriComponentsBuilder.queryParam("created_at_max", LocalDateTimeUtils.formatZone8(request.getCreateAtMax()));
        }
        if (StrUtil.isNotBlank(pageInfo)) {
            uriComponentsBuilder.queryParam("page_info", pageInfo);
        }
        if (request.hasId()) {
            uriComponentsBuilder.queryParam("since_id", sliceId != null? sliceId: request.getIdLongValue());
        }
        uriComponentsBuilder.queryParam("limit", "100");
        URI uri = uriComponentsBuilder.build().toUri();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer  " + thirdPartyWebsite.getToken());

        // 由于 GET 请求不带请求体，所以 `HttpEntity` 只包含 headers
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 发送 GET 请求
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

        String errors = "status: " + response.getStatusCode();
        if (StrUtil.isNotBlank(response.getBody())) {
            JSONObject entries = JSONUtil.parseObj(response.getBody());
            if (entries.containsKey("orders")) {
                convertAndSaveOrders(thirdPartyWebsite, entries.getJSONArray("orders"));
            }
            if (entries.containsKey("errors")) {
                errors = entries.get("errors", String.class);
            }
        }
        // 返回响应体
        throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(request.getIdLongValue(), errors);
    }

    private void convertAndSaveOrders(ThirdPartyWebsiteDto thirdPartyWebsite, JSONArray orders) {
        SystemUserDto owner = thirdPartyWebsite.getOwner();
        for (int i = 0; i < orders.size(); i++) {
            JSONObject jsonObject = orders.getJSONObject(i);
            buildOrderInfo(thirdPartyWebsite, owner, jsonObject);
        }
    }

    private void buildOrderInfo(ThirdPartyWebsiteDto thirdPartyWebsite, SystemUserDto owner, JSONObject order) {
        String currencyCode = order.getStr("currency");
        Optional<Currency> currencyOptional = currencyService.getByCode(currencyCode);
        String customerLocale = order.getStr("customer_locale");
        Pattern pattern = Pattern.compile("([a-z]+)([A-Z]+)");
        Matcher matcher = pattern.matcher(customerLocale);

        BigDecimal shippingFee = BigDecimal.ZERO;
        JSONArray shippingLines = order.getJSONArray("shipping_lines");
        if (!shippingLines.isEmpty()) {
            for (int i = 0; i < shippingLines.size(); i++) {
                JSONObject shippingLine = shippingLines.getJSONObject(i);
                if (shippingLine != null) {
                    String price = shippingLine.getStr("price");
                    if (price != null && !"0.00".equals(price)) {
                        shippingFee = shippingFee.add(new BigDecimal(price));
                    }
                }
            }
        }
        OrderFinancialInfo financialInfo = OrderFinancialInfo.builder()
                .totalAmount(new BigDecimal(StrUtil.blankToDefault(order.getStr("current_total_price"), "0")))
                .shippingFee(shippingFee)
                .discountAmount(new BigDecimal(StrUtil.blankToDefault(order.getStr("current_total_discounts"), "0")))
                .taxAmount(new BigDecimal(StrUtil.blankToDefault(order.getStr("current_total_tax"), "0")))
                .build();

        OrderPaymentInfo paymentInfo = OrderPaymentInfo.builder()
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.convertFromShopline(order.getStr("financial_status")))
                .paymentTime(LocalDateTime.now())
                .build();

        JSONObject clientDetails = order.getJSONObject("client_details");
        String browserIp = order.getStr("browser_ip");
        String uaStr = "";
        if (clientDetails != null) {
            uaStr = StrUtil.blankToDefault(clientDetails.getStr("user_agent"), "");
            browserIp = StrUtil.blankToDefault(clientDetails.getStr("browser_ip"), browserIp);
        }
        OrderRiskRecordInfo riskRecordInfo = OrderRiskRecordInfo.builder()
                .remoteIp(browserIp)
                .ua(uaStr)
                .browserPlatform(BrowserPlatform.fromUaStr(uaStr))
                .build();

        JSONObject customer = order.getJSONObject("customer");
        OrderDeliveryInfo.OrderDeliveryInfoBuilder<?, ?> orderDeliveryInfoBuilder = OrderDeliveryInfo.builder()
                .remark(StrUtil.blankToDefault(order.getStr("buyer_note"), ""));
        if (customer != null) {
            JSONObject addresses = customer.getJSONObject("addresses");
            if (addresses != null) {
                orderDeliveryInfoBuilder.email(StrUtil.blankToDefault(addresses.getStr("email"), ""))
                        .receiveUpdates(false)
                        .firstName(StrUtil.blankToDefault(addresses.getStr("first_name"), ""))
                        .lastName(StrUtil.blankToDefault(addresses.getStr("last_name"), ""))
                        .phone(StrUtil.blankToDefault(addresses.getStr("phone"), ""))
                        .province(StrUtil.blankToDefault(addresses.getStr("province"), ""))
                        .city(StrUtil.blankToDefault(addresses.getStr("city"), ""))
                        .district(StrUtil.blankToDefault(addresses.getStr("address2"), ""))
                        .postalCode(StrUtil.blankToDefault(addresses.getStr("zip"), ""))
                        .address(StrUtil.blankToDefault(addresses.getStr("address1"), ""))
                        .remoteArea(false);
            } else {
                orderDeliveryInfoBuilder.email(StrUtil.blankToDefault(customer.getStr("email"), ""))
                        .receiveUpdates(false)
                        .firstName(StrUtil.blankToDefault(customer.getStr("first_name"), ""))
                        .lastName(StrUtil.blankToDefault(customer.getStr("last_name"), ""))
                        .phone(StrUtil.blankToDefault(customer.getStr("phone"), ""));
            }
        }
        OrderDeliveryInfo orderDeliveryInfo = orderDeliveryInfoBuilder.build();
       /* // 订单上下文信息
        OrderContextInfo.OrderContextInfoBuilder<?, ?> orderContextInfoBuilder = OrderContextInfo.builder()
                .salesUid(Long.valueOf(owner.getId()))
                .salesPerson(owner.getName())
                .departmentId(owner.getDepartmentId())
                .department(owner.getDepartmentName())
                .websiteName(thirdPartyWebsite.getNickName())
                .websiteId(thirdPartyWebsite.getLongId())
                .websiteUrl("https://" + thirdPartyWebsite.getHandle() + ".myshopline.com/admin")
                .addressRule("")
                .phoneRule("");

        if (currencyOptional.isPresent()) {
            Currency currency = currencyOptional.get();
            orderContextInfoBuilder.currencyId(currency.getId())
                    .currencyCode(currency.getCode())
                    .currencySymbol(currency.getSymbol())
                    .currencyName(currency.getName())
                    .currencyFractionDigits(currency.getFractionDigits())
                    .currencyExchangeRate(currency.getExchangeRate());
        } else {
            orderContextInfoBuilder.currencyCode(StrUtil.isBlank(currencyCode) ? "" : currencyCode.trim().toUpperCase());
        }

        OrderContextInfo orderContextInfo = orderContextInfoBuilder.build();
        if (matcher.matches()) {
            String languageCode = matcher.group(1);
            String countryCode = matcher.group(2);
            Optional<Language> languageOptional = languageService.getByCode(languageCode);
            Optional<Country> countryOptional = countryService.getByCode(countryCode);
            if (languageOptional.isPresent()) {
                Language language = languageOptional.get();
                orderContextInfoBuilder.language(language.getName())
                        .languageCode(language.getCode())
                        .languageId(String.valueOf(language.getId()));
            } else {
                orderContextInfoBuilder.languageCode(StrUtil.isBlank(languageCode) ? "" : languageCode.trim().toUpperCase());
            }

            if (countryOptional.isPresent()) {
                Country country = countryOptional.get();
                orderContextInfoBuilder.country(country.getName())
                        .countryId(country.getId())
                        .countryCode(country.getCode());
            } else {
                orderContextInfoBuilder.countryCode(StrUtil.isBlank(countryCode) ? "" : countryCode.trim().toUpperCase());
            }
        }*/
        OrderBotCheckInfo botOrderCheckInfo = OrderBotCheckInfo.builder().build();
        OrderLogisticsInfo logisticsInfo = OrderLogisticsInfo.builder()
//                .waybillProductName(product.getWaybillProductName())
                .build();
        botOrderCheckInfo = botOrderCheckInfoRepository.save(botOrderCheckInfo);
        logisticsInfo = logisticsInfoRepository.save(logisticsInfo);


        JSONArray lineItems = order.getJSONArray("line_items");
        List<OrderItemInfo> orderItemInfoList = new ArrayList<>();
        if (lineItems != null && !lineItems.isEmpty()) {
            for (int i = 0; i < lineItems.size(); i++) {
                JSONObject lineItem = lineItems.getJSONObject(i);
                if (lineItem == null) {
                    continue;
                }
                OrderItemInfo orderItemInfo = OrderItemInfo.builder()
                        .spuId(0L)
                        .productId(0L)
                        .title(StrUtil.blankToDefault(lineItem.getStr("title"), ""))
                        .specTitle(StrUtil.blankToDefault(lineItem.getStr("attribute"), ""))
                        .sellPrice(new BigDecimal(StrUtil.blankToDefault(lineItem.getStr("price"), "0.00")))
                        .originPrice(BigDecimal.ZERO)
                        .costPrice(BigDecimal.ZERO)
                        .tax(BigDecimal.ZERO)
                        .barcode("")
                        .quantity(Long.parseLong(StrUtil.blankToDefault(lineItem.getStr("quantity"), "0")))
                        .skuId(0L)
                        .skuName(StrUtil.blankToDefault(lineItem.getStr("sku"), ""))
                        .skuCode(StrUtil.blankToDefault(lineItem.getStr("variant_id"), ""))
                        .skuIsVirtual(false)
                        .merchandise("")
                        .build();
                orderItemInfoList.add(orderItemInfo);
            }
        }
        Order orderInfo = Order.builder()
                .from(thirdPartyWebsite.getNickName())
                .fromUrl(StrUtil.blankToDefault(order.getStr("landing_site"), ""))
                .itemCount(1)
//                .items(orderItemInfoList)
//                .contextInfo(orderContextInfo)
                .deliveryInfo(orderDeliveryInfo)
                .financialInfo(financialInfo)
                .paymentInfo(paymentInfo)
                .botOrderCheckInfo(botOrderCheckInfo)
                .riskInfo(riskRecordInfo)
                .botOrderStatus(CheckStatus.PENDING)
//                .logisticsInfo(logisticsInfo)
                .orderStatus(OrderStatus.PENDING)
                .build();

        sliceId = StrUtil.blankToDefault(order.getStr("id"), "0");
//        orderItemInfoList.forEach((item) -> item.setOrder(orderInfo));
        orderInfo.setOwner(SystemUser.builder().id(thirdPartyWebsite.getOwner().getLongId()).build());
        orderService.saveAndFlush(orderInfo);
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
        // 提交异步任务执行
        taskService.submitAsyncTask(asyncTask.getId());
        // 返回任务ID
        return asyncTask.getId();
    }

    @Override
    protected void checkKeyConstraint(ThirdPartyWebsite data) {
        // 可添加验证逻辑，比如检查 token 或 appKey 的唯一性
    }

    @Transactional
    public ThirdPartyWebsiteDto getThirdPartyWebsiteDtoById(Long id) {
        ThirdPartyWebsite thirdPartyWebsite = getById(id);
        return ThirdPartyWebsiteDto.convert(thirdPartyWebsite);
    }
}
