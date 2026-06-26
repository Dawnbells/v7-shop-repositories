package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.controller.req.OrderStatisticsPageRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsBucketGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsGroupResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsMetricsResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsPageResponse;
import cn.v7soft.admin.controller.req.OrderStatisticsQueryRequest;
import cn.v7soft.admin.controller.resp.OrderStatisticsQueryResponse;
import cn.v7soft.admin.controller.resp.OrderStatisticsResultResponse;
import cn.v7soft.admin.service.IOrderStatisticsService;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Currency;
import cn.v7soft.dao.entities.primary.OrderStatisticsUserConfig;
import cn.v7soft.dao.repositories.primary.CurrencyRepository;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

@Service
public class OrderStatisticsSubmissionService {

    private final IOrderStatisticsService statisticsService;
    private final OrderStatisticsSnapshotService snapshotService;
    private final OrderStatisticsConfigService configService;
    private final ObjectMapper objectMapper;
    private final CurrencyRepository currencyRepository;

    public OrderStatisticsSubmissionService(
            IOrderStatisticsService statisticsService,
            OrderStatisticsSnapshotService snapshotService,
            OrderStatisticsConfigService configService,
            ObjectMapper objectMapper,
            CurrencyRepository currencyRepository
    ) {
        this.statisticsService = statisticsService;
        this.snapshotService = snapshotService;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.currencyRepository = currencyRepository;
    }

    public OrderStatisticsQueryResponse submit(OrderStatisticsQueryRequest request) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        OrderStatisticsUserConfig config = configService.getOrCreate(null);
        String fingerprint = fingerprint(request, user, config);
        boolean redisAvailable = true;

        if (!Boolean.TRUE.equals(request.getForceRefresh())) {
            try {
                String cachedToken = snapshotService.findCachedResultToken(
                        user.getCompanyId(),
                        user.getLongId(),
                        fingerprint
                );
                if (cachedToken != null && !cachedToken.isBlank()) {
                    try {
                        return completed(
                                snapshotService.get(
                                        user.getCompanyId(),
                                        user.getLongId(),
                                        cachedToken
                                ),
                                true
                        );
                    } catch (IllegalArgumentException ignored) {
                        // The result may have been removed while the short cache survived.
                    }
                }
            } catch (DataAccessException exception) {
                redisAvailable = false;
            }
        }

        OrderStatisticsResultResponse result = statisticsService.query(request);
        if (!redisAvailable) {
            return degraded(result);
        }
        try {
            OrderStatisticsStoredSnapshot snapshot = snapshotService.store(
                    user.getCompanyId(),
                    user.getLongId(),
                    result
            );
            snapshotService.cacheResultToken(
                    user.getCompanyId(),
                    user.getLongId(),
                    fingerprint,
                    snapshot.resultToken()
            );
            return completed(snapshot, false);
        } catch (DataAccessException exception) {
            return degraded(result);
        }
    }

    public OrderStatisticsResultResponse result(String resultToken) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        return snapshotService.get(
                user.getCompanyId(),
                user.getLongId(),
                resultToken
        ).result();
    }

    public OrderStatisticsPageResponse<OrderStatisticsGroupResponse> groupsPage(
            String resultToken,
            OrderStatisticsPageRequest request
    ) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        OrderStatisticsResultResponse result = snapshotService.get(
                user.getCompanyId(),
                user.getLongId(),
                resultToken
        ).result();
        return page(result.getGroups(), request, OrderStatisticsGroupResponse::getMetrics);
    }

    public OrderStatisticsPageResponse<OrderStatisticsBucketGroupResponse> bucketGroupsPage(
            String resultToken,
            OrderStatisticsPageRequest request
    ) {
        SystemUserDto user = SaSessionUtil.getLoginUser();
        OrderStatisticsResultResponse result = snapshotService.get(
                user.getCompanyId(),
                user.getLongId(),
                resultToken
        ).result();
        return page(result.getBucketGroups(), request, OrderStatisticsBucketGroupResponse::getMetrics);
    }

    private <T> OrderStatisticsPageResponse<T> page(
            List<T> items,
            OrderStatisticsPageRequest request,
            Function<T, OrderStatisticsMetricsResponse> metricsExtractor
    ) {
        List<T> source = sortItems(
                items == null ? List.of() : items, request, metricsExtractor);
        int pageNo = Math.max(1, request == null ? 1 : request.getPageNo());
        int pageSize = request == null ? 20 : request.getPageSize();
        pageSize = Math.min(100, Math.max(5, pageSize));
        int total = source.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return OrderStatisticsPageResponse.<T>builder()
                .list(source.subList(fromIndex, toIndex))
                .total(total)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .build();
    }
    private static final Set<String> SORTABLE_KEYS = Set.of(
            "orderCount", "validOrderCount", "invalidOrderCount",
            "deliveredOrderCount", "undeliveredOrderCount", "deliveryRate",
            "totalSalesAmount", "deliveredSalesAmount",
            "undeliveredSalesAmount", "invalidSalesAmount"
    );

    /**
     * 按 sortBy（"键 方向"）对全量快照排序后再分页（§9.6 服务端排序，合计基于全部分组）。
     * 默认/空/非白名单键（含父类默认 id desc）→ 保持快照原序（已按总销售额降序）。
     * 计数/金额/签收率统一按 BigDecimal 比较（避免字符串字典序）；null（如无有效订单的签收率）
     * 恒排末尾、不随方向翻转。
     */
    private <T> List<T> sortItems(
            List<T> source,
            OrderStatisticsPageRequest request,
            Function<T, OrderStatisticsMetricsResponse> metricsExtractor
    ) {
        if (request == null || source.size() < 2) {
            return source;
        }
        String sortBy = request.getSortBy();
        if (sortBy == null || sortBy.isBlank()) {
            return source;
        }
        String[] parts = sortBy.split(",")[0].trim().split("\\s+");
        String key = parts[0];
        if (!SORTABLE_KEYS.contains(key)) {
            return source;
        }
        boolean asc = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]);
        Comparator<BigDecimal> valueOrder =
                asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<T> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.<T, BigDecimal>comparing(
                item -> sortKey(metricsExtractor.apply(item), key),
                Comparator.nullsLast(valueOrder)
        ));
        return sorted;
    }

    private static BigDecimal sortKey(OrderStatisticsMetricsResponse metrics, String key) {
        if (metrics == null) {
            return null;
        }
        return switch (key) {
            case "orderCount" -> BigDecimal.valueOf(metrics.getOrderCount());
            case "validOrderCount" -> BigDecimal.valueOf(metrics.getValidOrderCount());
            case "invalidOrderCount" -> BigDecimal.valueOf(metrics.getInvalidOrderCount());
            case "deliveredOrderCount" -> BigDecimal.valueOf(metrics.getDeliveredOrderCount());
            case "undeliveredOrderCount" -> BigDecimal.valueOf(metrics.getUndeliveredOrderCount());
            case "deliveryRate" -> toAmount(metrics.getDeliveryRate());
            case "totalSalesAmount" -> toAmount(metrics.getTotalSalesAmount());
            case "deliveredSalesAmount" -> toAmount(metrics.getDeliveredSalesAmount());
            case "undeliveredSalesAmount" -> toAmount(metrics.getUndeliveredSalesAmount());
            case "invalidSalesAmount" -> toAmount(metrics.getInvalidSalesAmount());
            default -> null;
        };
    }

    private static BigDecimal toAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private OrderStatisticsQueryResponse completed(
            OrderStatisticsStoredSnapshot snapshot,
            boolean cached
    ) {
        return OrderStatisticsQueryResponse.builder()
                .state("COMPLETED")
                .resultToken(snapshot.resultToken())
                .snapshotExpiresAt(snapshot.expiresAt())
                .result(snapshot.result())
                .cached(cached)
                .degraded(false)
                .build();
    }

    private OrderStatisticsQueryResponse degraded(OrderStatisticsResultResponse result) {
        OrderStatisticsResultResponse limited = OrderStatisticsResultResponse.builder()
                .generatedAt(result.getGeneratedAt())
                .timeZoneId(result.getTimeZoneId())
                .targetCurrencyCode(result.getTargetCurrencyCode())
                .summary(result.getSummary())
                .buckets(result.getBuckets())
                .groups(result.getGroups() == null
                        ? List.of()
                        : result.getGroups().stream().limit(100).toList())
                .bucketGroups(result.getBucketGroups() == null
                        ? List.of()
                        : result.getBucketGroups().stream().limit(100).toList())
                .originalCurrencies(result.getOriginalCurrencies())
                .missingRates(result.getMissingRates())
                .build();
        return OrderStatisticsQueryResponse.builder()
                .state("COMPLETED")
                .result(limited)
                .cached(false)
                .degraded(true)
                .message("Redis 暂不可用，已返回同步降级结果；分页和导出不可用")
                .build();
    }

    private String fingerprint(
            OrderStatisticsQueryRequest request,
            SystemUserDto user,
            OrderStatisticsUserConfig config
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", request);
        payload.put("companyId", user.getCompanyId());
        payload.put("userId", user.getId());
        payload.put("userType", user.getUserType());
        payload.put("departmentId", user.getDepartmentId());
        payload.put("accessDepartmentIds", user.getAccessDepartmentIds());
        payload.put("parentDepartmentIds", user.getParentDepartmentIds());
        payload.put("crossDepartment", user.getIsCrossDepartment());
        payload.put("manageDepartmentIds", user.getManageDepartmentIds());
        payload.put("excludeDepartment", user.getIsExcludeDepartment());
        payload.put("viewMode", SaSessionUtil.getViewMode());
        payload.put("websiteScoped", WebsiteContext.isWebsiteAdmin());
        payload.put("websiteId", WebsiteContext.getCurrentWebsiteId());
        payload.put("timeZoneId", config.getTimeZoneId());
        payload.put("personalExchangeRates", config.getExchangeRates());
        payload.put("systemExchangeRates", systemRateFingerprint());
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("订单统计查询指纹生成失败", exception);
        }
    }

    /**
     * 公司当前有效系统币种汇率指纹：纳入缓存指纹后，管理员修改公司汇率会立即使 1 分钟查询缓存失效
     * （规格 §13.3 缓存指纹须包含生效汇率）。按币种代码排序保证确定性。
     */
    protected Map<String, String> systemRateFingerprint() {
        Map<String, String> rates = new TreeMap<>();
        for (Currency currency : currencyRepository.findAllValid()) {
            if (currency.getCode() != null && currency.getExchangeRate() != null) {
                rates.put(
                        currency.getCode().toUpperCase(Locale.ROOT),
                        currency.getExchangeRate().toPlainString()
                );
            }
        }
        return rates;
    }
}
