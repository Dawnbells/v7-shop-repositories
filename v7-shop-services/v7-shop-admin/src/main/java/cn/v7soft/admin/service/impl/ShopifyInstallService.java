package cn.v7soft.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IShopifyInstallService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.dto.ShopifyInstallTicket;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.admin.utils.ShopifyHmac;
import cn.v7soft.admin.utils.ShopifyHost;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shopify 公开应用的安装与绑定。
 * <p>
 * 安全约定（ARCH.md §9）：票据一次性、使用即删；日志不打印 token / refresh token / secret / code / 完整 nonce；
 * 公开端点设置租户前只用 {@link ShopifyWebsiteStore#findByShopDomain}（silent）查店铺。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyInstallService implements IShopifyInstallService {
    public static final String LANDING_PAGE = "/shopify-app/landing.html";
    static final String CALLBACK_PATH = "/shopify-app/oauth/callback";
    static final String CONNECT_PATH = "/shopify-app/connect";
    static final String WEBHOOK_PATH = "/shopify-app/webhooks";
    static final String ADMIN_BIND_PATH = "/#/shopify-bind";
    static final String ADMIN_HOST_PREFIX = "admin.";
    static final List<String> WEBHOOK_TOPICS = List.of("ORDERS_CREATE", "ORDERS_UPDATED", "ORDERS_CANCELLED", "APP_UNINSTALLED");

    private static final Pattern SHOP_DOMAIN = Pattern.compile("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    private static final Pattern HOST_NAME = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
    private static final String HANDLE_TAKEN_MESSAGE = "Handle已被占用: ";
    private static final String TICKET_EXPIRED_MESSAGE = "票据不存在或已过期";
    private static final String UNKNOWN_ORIGIN_MESSAGE = "无法识别当前后台地址，请从后台页面发起连接";

    static final String SHOP_NAME_QUERY = "query { shop { name } }";
    // 与 nick_name 列默认长度一致
    private static final int NICK_NAME_MAX_LENGTH = 255;

    static final String WEBHOOK_LIST_QUERY = """
            query {
              webhookSubscriptions(first: 50) {
                nodes { id topic uri }
              }
            }""";
    static final String WEBHOOK_CREATE_MUTATION = """
            mutation webhookSubscriptionCreate($topic: WebhookSubscriptionTopic!, $webhookSubscription: WebhookSubscriptionInput!) {
              webhookSubscriptionCreate(topic: $topic, webhookSubscription: $webhookSubscription) {
                webhookSubscription { id }
                userErrors { field message }
              }
            }""";

    private final ShopifyAppProperties appProperties;
    private final ShopifyInstallTicketStore ticketStore;
    private final ShopifyTokenService tokenService;
    private final ShopifyWebsiteStore websiteStore;
    private final ShopifyGraphqlClient graphqlClient;
    private final IThirdPartyWebsiteService websiteService;
    private final ICompanyService companyService;

    // ==================== 后台发起 ====================

    @Override
    public String createAdminInstallUrl(Long id, String nickName, CurrencyMode currencyMode,
                                        String returnOrigin, String originTopLevelDomain, Long companyId, Long userId) {
        requireConfigured();
        String origin = normalizeOrigin(returnOrigin);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(origin != null, UNKNOWN_ORIGIN_MESSAGE);
        Company originCompany = StrUtil.isBlank(originTopLevelDomain) ? null : companyService.identityCached(originTopLevelDomain);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(originCompany != null && companyId != null
                && Objects.equals(originCompany.getId(), companyId), UNKNOWN_ORIGIN_MESSAGE);

        String name = StrUtil.trimToNull(nickName);
        CurrencyMode mode = currencyMode != null ? currencyMode : CurrencyMode.SHOP_MONEY;
        if (id == null) {
            // 店铺由商家在 Shopify 中选择，名称为空时绑定后取 Shopify 店铺名
            ShopifyInstallTicket intent = new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.INTENT, null,
                    companyId, userId, name, mode, null, origin, null, null);
            String nonce = ticketStore.create(intent, Duration.ofMinutes(appProperties.getConnectIntentTtlMinutes()));
            log.info("Shopify connect intent created: companyId={}", companyId);
            return appHost() + CONNECT_PATH + "?intent=" + encode(nonce);
        }

        ThirdPartyWebsite record = websiteService.getById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(record.getWebsiteType() == WebsiteTypeEnum.SHOPIFY,
                "该商城不是Shopify类型");
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(Objects.equals(record.getCompanyId(), companyId)
                && ShopifyAccessMode.isPublicApp(record.getAccessMode()), "该商城不支持通过公开应用重新连接");
        String handle = record.getHandle();
        name = name != null ? name : record.getNickName();
        mode = currencyMode != null ? currencyMode : record.getCurrencyMode();

        ShopifyInstallTicket ticket = new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.ADMIN, handle,
                companyId, userId, name, mode, record.getId(), origin, null, null);
        String nonce = ticketStore.create(ticket, Duration.ofMinutes(appProperties.getInstallTicketTtlMinutes()));
        log.info("Shopify admin reconnect started: handle={}, companyId={}, websiteId={}", handle, companyId, record.getId());
        return authorizeUrl(handle, nonce);
    }

    // ==================== 公开端点 ====================

    @Override
    public Redirect handleConnect(String intent) {
        if (findIntent(intent).isEmpty()) {
            log.warn("Shopify connect intent not found or expired");
            return new Redirect(landingError(ErrorCode.TICKET_EXPIRED), IntentCookie.CLEAR, null);
        }
        String installUrl = StrUtil.trimToNull(appProperties.getInstallUrl());
        // 上架批准前没有 App Store 安装页：提示商家从 Shopify 后台打开应用，cookie 同样生效
        String location = installUrl != null ? installUrl : landingStatus("install");
        return new Redirect(location, IntentCookie.SET, intent);
    }

    @Override
    public Redirect handleLanding(Map<String, String[]> params, String intent) {
        if (!isConfigured()) {
            log.warn("Shopify landing opened but public app is not configured");
            return Redirect.of(landingError(ErrorCode.APP_NOT_CONFIGURED));
        }
        if (!ShopifyHmac.verifyQuery(params, appProperties.getClientSecret())) {
            log.warn("Shopify landing HMAC invalid");
            return Redirect.of(landingStatus("invalid"));
        }
        String shop = single(params, "shop");
        String handle = handleOfShop(shop);
        if (handle == null) {
            log.warn("Shopify landing shop invalid");
            return Redirect.of(landingStatus("invalid"));
        }
        Optional<ShopifyInstallTicket> intentTicket = findIntent(intent);
        // cookie 里的意向已过期或无效：顺手清掉
        IntentCookie staleCookie = intent != null && intentTicket.isEmpty() ? IntentCookie.CLEAR : IntentCookie.KEEP;

        Optional<ThirdPartyWebsite> existing = websiteStore.findByShopDomain(shop);
        if (existing.isPresent()) {
            ThirdPartyWebsite record = existing.get();
            if (record.getWebsiteType() != WebsiteTypeEnum.SHOPIFY || !ShopifyAccessMode.isPublicApp(record.getAccessMode())) {
                // 凭证模式（含 accessMode=null 存量）或非 Shopify 记录占用 handle：不进入 OAuth，
                // 避免应用装上后绑定失败、卸载时 webhook 打到凭证模式店铺
                log.warn("Shopify landing rejected, handle taken by non public-app record: handle={}", handle);
                return new Redirect(landingError(ErrorCode.HANDLE_TAKEN), staleCookie, null);
            }
            if (record.getAuthStatus() == ThirdPartyAuthStatusEnum.AUTHED) {
                return new Redirect(connectedLocation(record), staleCookie, null);
            }
            // 断开/异常的公开应用记录：继续授权（重连）
            if (intentTicket.isPresent() && !isReusableBy(record, intentTicket.get().companyId())) {
                // 意向保留：同一链接仍可用于安装其他店铺
                log.warn("Shopify landing rejected, handle taken by other tenant: handle={}, companyId={}",
                        handle, intentTicket.get().companyId());
                return Redirect.of(landingError(ErrorCode.HANDLE_TAKEN));
            }
        }

        if (intentTicket.isPresent()) {
            ShopifyInstallTicket source = intentTicket.get();
            ShopifyInstallTicket ticket = new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.INTENT, handle,
                    source.companyId(), source.userId(), source.nickName(), source.currencyMode(),
                    existing.map(ThirdPartyWebsite::getId).orElse(null), source.returnOrigin(), null, null);
            String nonce = ticketStore.create(ticket, Duration.ofMinutes(appProperties.getInstallTicketTtlMinutes()));
            // 意向一次性：换成带 handle 的票据后删除，并清 cookie
            ticketStore.delete(source.nonce());
            log.info("Shopify intent install started: handle={}, companyId={}", handle, source.companyId());
            return new Redirect(authorizeUrl(handle, nonce), IntentCookie.CLEAR, null);
        }

        ShopifyInstallTicket ticket = new ShopifyInstallTicket(null, ShopifyInstallTicket.TicketType.STORE, handle,
                null, null, null, null, null, null, null, null);
        String nonce = ticketStore.create(ticket, Duration.ofMinutes(appProperties.getInstallTicketTtlMinutes()));
        log.info("Shopify store install started: handle={}", handle);
        return new Redirect(authorizeUrl(handle, nonce), staleCookie, null);
    }

    @Override
    public String handleCallback(Map<String, String[]> params) {
        ShopifyInstallTicket ticket = ticketStore.get(single(params, "state")).orElse(null);
        if (ticket == null) {
            log.warn("Shopify OAuth callback state not found or expired");
            return landingStatus("invalid");
        }
        if (!isConfigured()) {
            return fail(ticket, ErrorCode.APP_NOT_CONFIGURED);
        }
        if (!ShopifyHmac.verifyQuery(params, appProperties.getClientSecret())) {
            return fail(ticket, ErrorCode.INVALID_HMAC);
        }
        String handle = handleOfShop(single(params, "shop"));
        if (handle == null) {
            return fail(ticket, ErrorCode.INVALID_SHOP);
        }
        if (!handle.equals(ticket.handle())
                || (ticket.type() == ShopifyInstallTicket.TicketType.STORE && ticket.pendingToken() != null)) {
            // 票据不属于该店铺，或 STORE 票据已经换过 token（回调重放）
            return fail(ticket, ErrorCode.INVALID_STATE);
        }
        String code = single(params, "code");
        if (StrUtil.isBlank(code)) {
            return fail(ticket, ErrorCode.TOKEN_EXCHANGE_FAILED);
        }

        ShopifyTokenService.ExchangedToken token;
        try {
            token = tokenService.exchangeAuthorizationCode(handle, code);
        } catch (RuntimeException e) {
            log.warn("Shopify OAuth code exchange failed: handle={}, error={}", handle, e.getClass().getSimpleName());
            return fail(ticket, ErrorCode.TOKEN_EXCHANGE_FAILED);
        }

        if (ticket.type() != ShopifyInstallTicket.TicketType.STORE) {
            return completeAdminInstall(ticket, token);
        }
        ticketStore.update(ticket.withPendingToken(token), Duration.ofMinutes(appProperties.getPendingTicketTtlMinutes()));
        log.info("Shopify store install authorized, waiting for bind: handle={}", handle);
        return LANDING_PAGE + "?ticket=" + encode(ticket.nonce());
    }

    @Override
    public String resolveLanding(String ticketNonce, String adminHost) {
        ShopifyInstallTicket ticket = ticketStore.get(ticketNonce).orElse(null);
        if (!isPendingStoreTicket(ticket)) {
            throw new InstallException(ErrorCode.TICKET_EXPIRED);
        }
        String host = normalizeHost(adminHost);
        String topLevelDomain = host == null ? null : topLevelDomain(host);
        if (topLevelDomain == null || companyService.identityCached(topLevelDomain) == null) {
            throw new InstallException(ErrorCode.UNKNOWN_HOST);
        }
        // 统一跳总后台 admin.{顶级域名}：后台登录页对非 admin. 开头的地址会跳到总后台登录且不带 redirect，bindTicket 会丢。
        // 参数名必须是 bindTicket：后台 SPA 会把 ticket 当作 SSO 票据处理
        return "https://" + ADMIN_HOST_PREFIX + topLevelDomain + ADMIN_BIND_PATH + "?bindTicket=" + encode(ticket.nonce());
    }

    // ==================== 绑定 ====================

    @Override
    public ThirdPartyWebsite bind(String ticketNonce, String nickName, CurrencyMode currencyMode, Long companyId, Long userId) {
        ShopifyInstallTicket ticket = ticketStore.get(ticketNonce).orElse(null);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(isPendingStoreTicket(ticket), TICKET_EXPIRED_MESSAGE);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(companyId != null, "无法识别当前租户");
        ThirdPartyWebsite saved;
        try {
            saved = upsert(ticket.handle(), companyId, userId, nickName, currencyMode, ticket.pendingToken());
        } catch (InstallException e) {
            throw ClientResponseEnum.PARAMETER_ILLEGAL.newException(HANDLE_TAKEN_MESSAGE + ticket.handle());
        }
        ticketStore.delete(ticket.nonce());
        log.info("Shopify store bound: handle={}, websiteId={}, companyId={}", ticket.handle(), saved.getId(), companyId);
        return saved;
    }

    @Override
    public ThirdPartyWebsite upsert(String handle, Long companyId, Long userId, String nickName, CurrencyMode currencyMode,
                                    ShopifyTokenService.ExchangedToken token) {
        LocalDateTime now = LocalDateTime.now();
        Optional<ThirdPartyWebsite> existing = websiteStore.findByShopDomain(ShopifyHost.host(handle));
        ThirdPartyWebsite website;
        if (existing.isPresent()) {
            website = existing.get();
            if (!isReusableBy(website, companyId)) {
                log.warn("Shopify install rejected, handle taken: handle={}, companyId={}", handle, companyId);
                throw new InstallException(ErrorCode.HANDLE_TAKEN);
            }
            if (website.getUninstalledAt() != null) {
                // 卸载期间的订单由对账从卸载前回看补齐
                website.setLastReconcileTime(website.getUninstalledAt().minusMinutes(appProperties.getReconcileLookbackMinutes()));
            }
            if (StrUtil.isNotBlank(nickName)) {
                website.setNickName(nickName.trim());
            }
            if (currencyMode != null) {
                website.setCurrencyMode(currencyMode);
            }
            website.setUninstalledAt(null);
        } else {
            website = ThirdPartyWebsite.builder()
                    .websiteType(WebsiteTypeEnum.SHOPIFY)
                    .accessMode(ShopifyAccessMode.PUBLIC_APP)
                    .handle(handle)
                    .nickName(StrUtil.isNotBlank(nickName) ? nickName.trim() : handle)
                    .currencyMode(currencyMode != null ? currencyMode : CurrencyMode.SHOP_MONEY)
                    .lastSyncTime(now)
                    // 只同步绑定之后的订单
                    .lastSyncOrderTime(now)
                    .build();
            website.setCompanyId(companyId);
        }
        website.setAuthStatus(ThirdPartyAuthStatusEnum.AUTHED);
        website.setAuthMessage(null);
        website.setStatus(StatusEnum.VALID);
        website.setOwner(SystemUser.builder().id(userId).build());
        website.setToken(token.accessToken());
        website.setTokenExpiresAt(token.accessExpiresAt());
        website.setRefreshToken(token.refreshToken());
        website.setRefreshTokenExpiresAt(token.refreshExpiresAt());
        website.setGrantedScopes(token.scope());
        website.setHasAllOrdersScope(ShopifyWebsiteStore.hasAllOrdersScope(token.scope()));
        website.setInstalledAt(now);

        ThirdPartyWebsite saved;
        try {
            saved = websiteService.save(website);
        } catch (DataIntegrityViolationException e) {
            // 异常信息可能含 token 原文，不打印；多为其他租户占用了 handle
            log.warn("Shopify install save conflict: handle={}, companyId={}", handle, companyId);
            throw new InstallException(ErrorCode.HANDLE_TAKEN);
        }
        log.info("Shopify public app website {}: handle={}, websiteId={}, companyId={}",
                existing.isPresent() ? "restored" : "created", handle, saved.getId(), companyId);
        if (existing.isEmpty() && StrUtil.isBlank(nickName)) {
            saved = fillShopName(saved);
        }
        registerWebhooksIfEnabled(saved);
        return saved;
    }

    // ==================== 内部 ====================

    private String completeAdminInstall(ShopifyInstallTicket ticket, ShopifyTokenService.ExchangedToken token) {
        try {
            TenantContext.setCurrentTenant(ticket.companyId(), companyService.companyCached(ticket.companyId()));
            upsert(ticket.handle(), ticket.companyId(), ticket.userId(), ticket.nickName(), ticket.currencyMode(), token);
            ticketStore.delete(ticket.nonce());
            if (ticket.type() == ShopifyInstallTicket.TicketType.INTENT) {
                // 授权的浏览器不一定登录了后台（链接可能转给店主），结果显示在落地页
                return LANDING_PAGE + "?status=linked&handle=" + encode(ticket.handle());
            }
            return adminResult(ticket, "success", null);
        } catch (InstallException e) {
            return fail(ticket, e.getCode());
        } catch (RuntimeException e) {
            log.error("Shopify admin install failed: handle={}, companyId={}, error={}",
                    ticket.handle(), ticket.companyId(), e.getClass().getSimpleName());
            return fail(ticket, ErrorCode.INVALID_STATE);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 失败：删除票据，ADMIN 回后台绑定页，INTENT / STORE 回结果页
     */
    private String fail(ShopifyInstallTicket ticket, ErrorCode code) {
        log.warn("Shopify OAuth callback rejected: type={}, handle={}, code={}", ticket.type(), ticket.handle(), code);
        ticketStore.delete(ticket.nonce());
        if (ticket.type() == ShopifyInstallTicket.TicketType.ADMIN && StrUtil.isNotBlank(ticket.returnOrigin())) {
            return adminResult(ticket, "error", code);
        }
        return switch (code) {
            case TOKEN_EXCHANGE_FAILED, APP_NOT_CONFIGURED, HANDLE_TAKEN -> landingError(code);
            default -> landingStatus("invalid");
        };
    }

    /**
     * 未消费的连接意向（INTENT 且尚未确定店铺）
     */
    private Optional<ShopifyInstallTicket> findIntent(String intent) {
        if (StrUtil.isBlank(intent)) {
            return Optional.empty();
        }
        return ticketStore.get(intent)
                .filter(t -> t.type() == ShopifyInstallTicket.TicketType.INTENT && t.handle() == null);
    }

    /**
     * 已连接的店铺从 Shopify 打开应用：跳到所属租户的总后台 admin.{domain}；取不到域名时停在结果页
     */
    private String connectedLocation(ThirdPartyWebsite record) {
        Company company = record.getCompanyId() == null ? null : companyService.companyCached(record.getCompanyId());
        String domain = company == null ? null : StrUtil.trimToNull(company.getDomain());
        if (domain == null || !HOST_NAME.matcher(domain.toLowerCase(Locale.ROOT)).matches()) {
            return landingStatus("connected");
        }
        return "https://" + ADMIN_HOST_PREFIX + domain.toLowerCase(Locale.ROOT) + "/";
    }

    private String adminResult(ShopifyInstallTicket ticket, String status, ErrorCode code) {
        StringBuilder url = new StringBuilder(ticket.returnOrigin()).append(ADMIN_BIND_PATH)
                .append("?status=").append(status);
        if (code != null) {
            url.append("&code=").append(code.name());
        }
        url.append("&handle=").append(encode(StrUtil.nullToEmpty(ticket.handle())));
        return url.toString();
    }

    /**
     * 新建时未填名称：用 Shopify 店铺名代替默认的 handle，查询失败时保留 handle
     */
    private ThirdPartyWebsite fillShopName(ThirdPartyWebsite website) {
        try {
            ShopifyGraphqlClient.GraphqlResult result = graphqlClient.execute(toDto(website), SHOP_NAME_QUERY);
            String shopName = result == null || result.data() == null ? null
                    : StrUtil.trimToNull(result.data().getByPath("shop.name", String.class));
            if (shopName == null) {
                return website;
            }
            website.setNickName(StrUtil.sub(shopName, 0, NICK_NAME_MAX_LENGTH));
            return websiteService.save(website);
        } catch (RuntimeException e) {
            log.warn("Shopify shop name lookup failed: websiteId={}, handle={}, error={}",
                    website.getId(), website.getHandle(), e.getClass().getSimpleName());
            return website;
        }
    }

    private void registerWebhooksIfEnabled(ThirdPartyWebsite website) {
        if (!appProperties.isRegisterWebhooksOnInstall()) {
            return;
        }
        String callbackUrl = appHost() + WEBHOOK_PATH;
        ThirdPartyWebsiteDto dto = toDto(website);
        try {
            ShopifyGraphqlClient.GraphqlResult listed = graphqlClient.execute(dto, WEBHOOK_LIST_QUERY);
            Set<String> registered = registeredTopics(listed, callbackUrl);
            for (String topic : WEBHOOK_TOPICS) {
                if (registered.contains(topic)) {
                    continue;
                }
                Map<String, Object> subscription = new LinkedHashMap<>();
                subscription.put("uri", callbackUrl);
                subscription.put("format", "JSON");
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("topic", topic);
                variables.put("webhookSubscription", subscription);
                ShopifyGraphqlClient.GraphqlResult created = graphqlClient.execute(dto, WEBHOOK_CREATE_MUTATION, variables);
                JSONArray userErrors = created == null || created.data() == null ? null
                        : created.data().getByPath("webhookSubscriptionCreate.userErrors", JSONArray.class);
                if (userErrors != null && !userErrors.isEmpty()) {
                    log.warn("Shopify webhook create rejected: websiteId={}, handle={}, topic={}, errors={}",
                            website.getId(), website.getHandle(), topic, userErrors);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Shopify webhook registration failed: websiteId={}, handle={}, error={}",
                    website.getId(), website.getHandle(), e.getClass().getSimpleName());
        }
    }

    private static Set<String> registeredTopics(ShopifyGraphqlClient.GraphqlResult result, String callbackUrl) {
        Set<String> topics = new HashSet<>();
        if (result == null || result.data() == null) {
            return topics;
        }
        JSONArray nodes = result.data().getByPath("webhookSubscriptions.nodes", JSONArray.class);
        if (nodes == null) {
            return topics;
        }
        for (Object item : nodes) {
            if (item instanceof JSONObject node && callbackUrl.equals(node.getStr("uri"))) {
                topics.add(node.getStr("topic"));
            }
        }
        return topics;
    }

    private static ThirdPartyWebsiteDto toDto(ThirdPartyWebsite website) {
        // 不用 ThirdPartyWebsiteDto.convert：owner 只是带 id 的引用，转换角色/部门会失败
        return ThirdPartyWebsiteDto.builder()
                .id(String.valueOf(website.getId()))
                .nickName(website.getNickName())
                .handle(website.getHandle())
                .token(website.getToken())
                .tokenExpiresAt(website.getTokenExpiresAt())
                .authStatus(website.getAuthStatus())
                .websiteType(website.getWebsiteType())
                .currencyMode(website.getCurrencyMode())
                .accessMode(website.getAccessMode())
                .refreshToken(website.getRefreshToken())
                .refreshTokenExpiresAt(website.getRefreshTokenExpiresAt())
                .hasAllOrdersScope(Boolean.TRUE.equals(website.getHasAllOrdersScope()))
                .lastReconcileTime(website.getLastReconcileTime())
                .companyId(website.getCompanyId())
                .build();
    }

    /**
     * 记录可被该租户的公开应用安装复用：同租户、Shopify、公开应用
     */
    private static boolean isReusableBy(ThirdPartyWebsite record, Long companyId) {
        return Objects.equals(record.getCompanyId(), companyId)
                && record.getWebsiteType() == WebsiteTypeEnum.SHOPIFY
                && ShopifyAccessMode.isPublicApp(record.getAccessMode());
    }

    private static boolean isPendingStoreTicket(ShopifyInstallTicket ticket) {
        return ticket != null && ticket.type() == ShopifyInstallTicket.TicketType.STORE && ticket.pendingToken() != null;
    }

    private String authorizeUrl(String handle, String nonce) {
        return "https://" + ShopifyHost.host(handle) + "/admin/oauth/authorize"
                + "?client_id=" + encode(appProperties.getClientId())
                + "&scope=" + encode(StrUtil.nullToEmpty(appProperties.getScopes()))
                + "&redirect_uri=" + encode(appHost() + CALLBACK_PATH)
                + "&state=" + encode(nonce);
    }

    private boolean isConfigured() {
        return appProperties.isConfigured() && StrUtil.isNotBlank(appProperties.getAppHost());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw ServiceResponseEnum.ERR_SHOPIFY_APP_NOT_CONFIGURED.newException();
        }
    }

    private String appHost() {
        return StrUtil.removeSuffix(StrUtil.trim(appProperties.getAppHost()), "/");
    }

    private static String landingStatus(String status) {
        return LANDING_PAGE + "?status=" + status;
    }

    private static String landingError(ErrorCode code) {
        return LANDING_PAGE + "?status=error&code=" + code.name();
    }

    private static String single(Map<String, String[]> params, String name) {
        if (params == null) {
            return null;
        }
        String[] values = params.get(name);
        return values == null || values.length != 1 ? null : values[0];
    }

    /**
     * xxx.myshopify.com → xxx；格式不合法返回 null
     */
    static String handleOfShop(String shop) {
        if (shop == null || !SHOP_DOMAIN.matcher(shop).matches()) {
            return null;
        }
        try {
            return ShopifyHost.normalize(shop);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Origin / Referer → scheme://host[:port]；不是 http(s) 绝对地址返回 null
     */
    static String normalizeOrigin(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return null;
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            return null;
        }
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    /**
     * 商家输入的后台地址 → host（允许带协议、路径）；含端口、用户信息或格式不合法返回 null
     */
    static String normalizeHost(String input) {
        if (StrUtil.isBlank(input)) {
            return null;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            value = "https://" + value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getPort() != -1 || !HOST_NAME.matcher(host).matches()) {
            return null;
        }
        return host;
    }

    /**
     * 与 DomainUtils 一致：取 host 的最后两段作为顶级域名，至少需要三段（如 admin.example.com）
     */
    static String topLevelDomain(String host) {
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return null;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
