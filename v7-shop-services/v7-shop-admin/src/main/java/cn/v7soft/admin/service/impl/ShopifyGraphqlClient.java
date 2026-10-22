package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.core.enums.ServiceResponseEnum;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shopify Admin GraphQL API 客户端（凭证模式与公开应用共用）。
 * <ul>
 *     <li>POST https://{handle}.myshopify.com/admin/api/{api-version}/graphql.json，头 X-Shopify-Access-Token</li>
 *     <li>401：强制刷新 token 后重试一次</li>
 *     <li>errors[].extensions.code == THROTTLED：按 extensions.cost.throttleStatus 等待后重试，最多 3 次</li>
 *     <li>其他 errors 非空：抛 ERR_TOKEN_INVALID</li>
 *     <li>HTTP 402/423：标记店铺授权异常并停用后抛出原异常</li>
 * </ul>
 * 每个店铺共用一个 2 次/秒的限流器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyGraphqlClient {
    static final int MAX_THROTTLE_RETRIES = 3;
    private static final int MAX_NETWORK_RETRIES = 2;
    private static final long DEFAULT_THROTTLE_WAIT_MILLIS = 1000;
    private static final long MIN_THROTTLE_WAIT_MILLIS = 500;
    private static final long MAX_THROTTLE_WAIT_MILLIS = 10_000;
    private static final int RATE_LIMIT_PER_SECOND = 2;

    private final RestTemplate restTemplate;
    private final ShopifyTokenService tokenService;
    private final ShopifyWebsiteStore websiteStore;
    private final ShopifyAppProperties appProperties;
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * GraphQL 响应。成功返回时 errors 为 null 或空
     */
    public record GraphqlResult(JSONObject data, JSONArray errors, JSONObject extensions) {
    }

    public GraphqlResult execute(ThirdPartyWebsiteDto website, String query) {
        return execute(website, query, null);
    }

    public GraphqlResult execute(ThirdPartyWebsiteDto website, String query, Map<String, Object> variables) {
        Long websiteId = website.getLongId();
        String handle = website.getHandle();
        // 在发出请求前校验 handle（包括库里已存的值）
        String url = "https://" + ShopifyHost.host(handle) + "/admin/api/" + apiVersion() + "/graphql.json";

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        requestBody.put("variables", variables == null ? Collections.emptyMap() : variables);

        String token = tokenService.getAccessToken(website);
        boolean tokenRefreshed = false;
        int throttleRetries = 0;
        int networkRetries = 0;
        while (true) {
            ResponseEntity<String> response;
            try {
                response = post(handle, url, token, requestBody);
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401 && !tokenRefreshed) {
                    log.warn("Shopify GraphQL token rejected, refreshing: websiteId={}, handle={}", websiteId, handle);
                    token = tokenService.forceRefresh(websiteId, token);
                    tokenRefreshed = true;
                    continue;
                }
                if (status == 429 && throttleRetries < MAX_THROTTLE_RETRIES) {
                    throttleRetries++;
                    sleep(DEFAULT_THROTTLE_WAIT_MILLIS);
                    continue;
                }
                String unavailableMessage = shopUnavailableMessage(status);
                if (unavailableMessage != null) {
                    websiteStore.markWebsiteAuthError(websiteId, unavailableMessage);
                }
                log.error("Shopify GraphQL request failed: websiteId={}, handle={}, status={}", websiteId, handle, status);
                throw e;
            } catch (ResourceAccessException e) {
                if (networkRetries < MAX_NETWORK_RETRIES) {
                    networkRetries++;
                    log.warn("Shopify GraphQL network error, retrying: websiteId={}, handle={}, attempt={}",
                            websiteId, handle, networkRetries);
                    sleep(DEFAULT_THROTTLE_WAIT_MILLIS);
                    continue;
                }
                throw e;
            }

            if (StrUtil.isBlank(response.getBody())) {
                throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(websiteId,
                        "Shopify GraphQL 返回空响应 (HTTP " + response.getStatusCode().value() + ")");
            }
            JSONObject body = JSONUtil.parseObj(response.getBody());
            JSONObject extensions = body.getJSONObject("extensions");
            JSONArray errors = readErrors(body);
            if (errors != null && !errors.isEmpty()) {
                if (isThrottled(errors) && throttleRetries < MAX_THROTTLE_RETRIES) {
                    throttleRetries++;
                    long waitMillis = throttleWaitMillis(extensions);
                    log.info("Shopify GraphQL throttled, waiting {}ms: websiteId={}, handle={}, retry={}",
                            waitMillis, websiteId, handle, throttleRetries);
                    sleep(waitMillis);
                    continue;
                }
                log.warn("Shopify GraphQL errors: websiteId={}, handle={}, errors={}", websiteId, handle, errors);
                throw ServiceResponseEnum.ERR_TOKEN_INVALID.newException(websiteId, errors.toString());
            }
            return new GraphqlResult(body.getJSONObject("data"), errors, extensions);
        }
    }

    /**
     * 等待（测试中可覆盖）
     */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Shopify GraphQL 退避等待被中断", e);
        }
    }

    /**
     * 按 extensions.cost 计算限流等待时长：(requestedQueryCost - currentlyAvailable) / restoreRate 秒
     */
    static long throttleWaitMillis(JSONObject extensions) {
        JSONObject cost = extensions == null ? null : extensions.getJSONObject("cost");
        JSONObject throttleStatus = cost == null ? null : cost.getJSONObject("throttleStatus");
        if (throttleStatus == null) {
            return DEFAULT_THROTTLE_WAIT_MILLIS;
        }
        Double available = throttleStatus.getDouble("currentlyAvailable");
        Double restoreRate = throttleStatus.getDouble("restoreRate");
        Double requested = cost.getDouble("requestedQueryCost");
        if (available == null || restoreRate == null || restoreRate <= 0) {
            return DEFAULT_THROTTLE_WAIT_MILLIS;
        }
        double needed = (requested == null ? restoreRate : requested) - available;
        long millis = (long) Math.ceil(Math.max(needed, 0) / restoreRate * 1000);
        return Math.min(Math.max(millis, MIN_THROTTLE_WAIT_MILLIS), MAX_THROTTLE_WAIT_MILLIS);
    }

    private ResponseEntity<String> post(String handle, String url, String token, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Shopify-Access-Token", token);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        return RateLimiter.decorateSupplier(rateLimiterOf(handle),
                () -> restTemplate.postForEntity(url, entity, String.class)).get();
    }

    /**
     * errors 通常为数组；个别错误场景为字符串，统一包装成数组
     */
    private static JSONArray readErrors(JSONObject body) {
        Object raw = body.get("errors");
        if (raw == null) {
            return null;
        }
        if (raw instanceof JSONArray array) {
            return array;
        }
        JSONArray wrapped = new JSONArray();
        wrapped.add(new JSONObject().set("message", String.valueOf(raw)));
        return wrapped;
    }

    private static boolean isThrottled(JSONArray errors) {
        for (int i = 0; i < errors.size(); i++) {
            Object item = errors.get(i);
            if (item instanceof JSONObject error) {
                JSONObject extensions = error.getJSONObject("extensions");
                if (extensions != null && "THROTTLED".equals(extensions.getStr("code"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String shopUnavailableMessage(int statusCode) {
        return switch (statusCode) {
            case 402 -> "Shopify店铺已冻结或不可用，请登录Shopify后台检查店铺状态及账单 (HTTP 402)";
            case 423 -> "Shopify店铺已锁定，请联系Shopify支持检查店铺状态 (HTTP 423)";
            default -> null;
        };
    }

    private String apiVersion() {
        String version = appProperties == null ? null : appProperties.getApiVersion();
        return StrUtil.isBlank(version) ? "2026-07" : version;
    }

    private RateLimiter rateLimiterOf(String handle) {
        return rateLimiters.computeIfAbsent(handle, h -> RateLimiter.of("shopify-graphql-" + h, RateLimiterConfig.custom()
                .limitForPeriod(RATE_LIMIT_PER_SECOND)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(10))
                .build()));
    }
}
