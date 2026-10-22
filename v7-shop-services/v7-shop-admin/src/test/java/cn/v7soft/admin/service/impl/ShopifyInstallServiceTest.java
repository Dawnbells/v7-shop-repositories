package cn.v7soft.admin.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.IShopifyInstallService.ErrorCode;
import cn.v7soft.admin.service.IShopifyInstallService.InstallException;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.admin.service.dto.ShopifyInstallTicket;
import cn.v7soft.admin.service.dto.ShopifyInstallTicket.TicketType;
import cn.v7soft.admin.service.dto.ThirdPartyWebsiteDto;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.ServiceResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopifyInstallServiceTest {
    private static final String CLIENT_ID = "app-client-id";
    private static final String CLIENT_SECRET = "app-client-secret-value";
    private static final String APP_HOST = "https://shopify.vantasite.test";
    private static final String NONCE = "a".repeat(64);
    private static final String ACCESS_TOKEN = "shpat_secret_access_token";
    private static final String REFRESH_TOKEN = "shprt_secret_refresh_token";
    private static final String AUTH_CODE = "oauth-code-secret";
    private static final Long COMPANY_ID = 7L;
    private static final Long OTHER_COMPANY_ID = 8L;
    private static final Long USER_ID = 501L;
    private static final String RETURN_ORIGIN = "https://admin.example.com";

    @Mock private ShopifyInstallTicketStore ticketStore;
    @Mock private ShopifyTokenService tokenService;
    @Mock private ShopifyWebsiteStore websiteStore;
    @Mock private ShopifyGraphqlClient graphqlClient;
    @Mock private IThirdPartyWebsiteService websiteService;
    @Mock private ICompanyService companyService;

    private ShopifyAppProperties properties;
    private ShopifyInstallService service;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        properties = new ShopifyAppProperties();
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(CLIENT_SECRET);
        properties.setAppHost(APP_HOST + "/");
        properties.setScopes("read_orders,read_products");
        service = new ShopifyInstallService(properties, ticketStore, tokenService, websiteStore, graphqlClient,
                websiteService, companyService);

        when(ticketStore.create(any(), any())).thenReturn(NONCE);
        when(websiteStore.findByShopDomain(anyString())).thenReturn(Optional.empty());
        when(websiteService.save(any(ThirdPartyWebsite.class))).thenAnswer(inv -> {
            ThirdPartyWebsite w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(900L);
            }
            return w;
        });
        when(companyService.identityCached("example.com")).thenReturn(company(COMPANY_ID));
        when(companyService.companyCached(COMPANY_ID)).thenReturn(company(COMPANY_ID));

        serviceLogger = (Logger) LoggerFactory.getLogger(ShopifyInstallService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        TenantContext.clear();
        // 任何日志不得包含 token / refresh token / secret / code / 完整 nonce
        for (ILoggingEvent event : logAppender.list) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains(ACCESS_TOKEN), message);
            assertFalse(message.contains(REFRESH_TOKEN), message);
            assertFalse(message.contains(CLIENT_SECRET), message);
            assertFalse(message.contains(AUTH_CODE), message);
            assertFalse(message.contains(NONCE), message);
        }
    }

    // ==================== install-url ====================

    @Nested
    @DisplayName("createAdminAuthorizeUrl")
    class AdminAuthorizeUrl {

        @Test
        @DisplayName("规范化 handle，签发 ADMIN 票据，返回含 client_id/scope/redirect_uri/state 的授权 URL")
        void shouldBuildAuthorizeUrl() {
            String url = service.createAdminAuthorizeUrl(null, "店铺A", "https://Shop-A.myshopify.com",
                    CurrencyMode.PRESENTMENT_MONEY, "https://admin.example.com/some/path?x=1", "example.com", COMPANY_ID, USER_ID);

            URI uri = URI.create(url);
            assertEquals("https", uri.getScheme());
            assertEquals("shop-a.myshopify.com", uri.getHost());
            assertEquals("/admin/oauth/authorize", uri.getPath());
            Map<String, String> query = parseQuery(uri.getRawQuery());
            assertEquals(CLIENT_ID, query.get("client_id"));
            assertEquals("read_orders,read_products", query.get("scope"));
            assertEquals(APP_HOST + "/shopify-app/oauth/callback", query.get("redirect_uri"));
            assertEquals(NONCE, query.get("state"));

            ArgumentCaptor<ShopifyInstallTicket> ticket = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).create(ticket.capture(), eq(Duration.ofMinutes(10)));
            ShopifyInstallTicket saved = ticket.getValue();
            assertEquals(TicketType.ADMIN, saved.type());
            assertEquals("shop-a", saved.handle());
            assertEquals(COMPANY_ID, saved.companyId());
            assertEquals(USER_ID, saved.userId());
            assertEquals("店铺A", saved.nickName());
            assertEquals(CurrencyMode.PRESENTMENT_MONEY, saved.currencyMode());
            assertEquals(RETURN_ORIGIN, saved.returnOrigin());
            assertNull(saved.websiteId());
            assertNull(saved.pendingToken());
        }

        @Test
        @DisplayName("名称与币种缺省：名称取 handle，币种 SHOP_MONEY；回跳 origin 保留端口")
        void shouldDefaultNameAndCurrency() {
            service.createAdminAuthorizeUrl(null, " ", "shop-a", null, "http://admin.example.com:5200", "example.com",
                    COMPANY_ID, USER_ID);

            ArgumentCaptor<ShopifyInstallTicket> ticket = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).create(ticket.capture(), any());
            assertEquals("shop-a", ticket.getValue().nickName());
            assertEquals(CurrencyMode.SHOP_MONEY, ticket.getValue().currencyMode());
            assertEquals("http://admin.example.com:5200", ticket.getValue().returnOrigin());
        }

        @Test
        @DisplayName("handle 被其他租户占用 → Handle已被占用，不签发票据")
        void shouldRejectHandleOfOtherTenant() {
            when(websiteStore.findByShopDomain("shop-a.myshopify.com"))
                    .thenReturn(Optional.of(publicAppWebsite(1L, OTHER_COMPANY_ID)));

            BaseException e = assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a",
                    null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID));
            assertEquals(ClientResponseEnum.PARAMETER_ILLEGAL, e.getResponseEnum());
            assertTrue(e.getMessage().contains("Handle已被占用"));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("当前租户的凭证模式记录占用 handle（含 accessMode=null 存量）→ Handle已被占用")
        void shouldRejectHandleOfCredentialsRecord() {
            ThirdPartyWebsite legacy = publicAppWebsite(1L, COMPANY_ID);
            legacy.setAccessMode(null);
            when(websiteStore.findByShopDomain("shop-a.myshopify.com")).thenReturn(Optional.of(legacy));

            BaseException e = assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a",
                    null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID));
            assertTrue(e.getMessage().contains("Handle已被占用"));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("当前租户已有公开应用记录 → 视为重连，票据带 websiteId")
        void shouldTreatExistingPublicAppAsReconnect() {
            when(websiteStore.findByShopDomain("shop-a.myshopify.com"))
                    .thenReturn(Optional.of(publicAppWebsite(55L, COMPANY_ID)));

            service.createAdminAuthorizeUrl(null, "A", "shop-a", null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID);

            ArgumentCaptor<ShopifyInstallTicket> ticket = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).create(ticket.capture(), any());
            assertEquals(55L, ticket.getValue().websiteId());
        }

        @Test
        @DisplayName("带 id 重连：handle 可空，名称与币种沿用原记录")
        void shouldReconnectById() {
            ThirdPartyWebsite record = publicAppWebsite(55L, COMPANY_ID);
            record.setNickName("原名称");
            record.setCurrencyMode(CurrencyMode.PRESENTMENT_MONEY);
            when(websiteService.getById(55L)).thenReturn(record);

            service.createAdminAuthorizeUrl(55L, null, null, null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID);

            ArgumentCaptor<ShopifyInstallTicket> ticket = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).create(ticket.capture(), any());
            assertEquals(55L, ticket.getValue().websiteId());
            assertEquals("shop-a", ticket.getValue().handle());
            assertEquals("原名称", ticket.getValue().nickName());
            assertEquals(CurrencyMode.PRESENTMENT_MONEY, ticket.getValue().currencyMode());
        }

        @Test
        @DisplayName("带 id 重连凭证模式记录 → 拒绝")
        void shouldRejectReconnectOfCredentialsRecord() {
            ThirdPartyWebsite record = publicAppWebsite(55L, COMPANY_ID);
            record.setAccessMode(ShopifyAccessMode.CREDENTIALS);
            when(websiteService.getById(55L)).thenReturn(record);

            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(55L, null, null, null,
                    RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("回跳来源解析不到当前租户（其他公司 / 未登记 / 缺 Origin）→ PARAMETER_ILLEGAL")
        void shouldRejectOriginOfOtherTenant() {
            when(companyService.identityCached("other.com")).thenReturn(company(OTHER_COMPANY_ID));

            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a", null,
                    "https://admin.other.com", "other.com", COMPANY_ID, USER_ID));
            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a", null,
                    "https://admin.unknown.com", "unknown.com", COMPANY_ID, USER_ID));
            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a", null,
                    null, "example.com", COMPANY_ID, USER_ID));
            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "shop-a", null,
                    "javascript:alert(1)", "example.com", COMPANY_ID, USER_ID));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("非法 handle → PARAMETER_ILLEGAL")
        void shouldRejectInvalidHandle() {
            assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A", "https://evil.com",
                    null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("公开应用未配置 → ERR_SHOPIFY_APP_NOT_CONFIGURED")
        void shouldFailWhenNotConfigured() {
            properties.setClientSecret("");

            BaseException e = assertThrows(BaseException.class, () -> service.createAdminAuthorizeUrl(null, "A",
                    "shop-a", null, RETURN_ORIGIN, "example.com", COMPANY_ID, USER_ID));
            assertEquals(ServiceResponseEnum.ERR_SHOPIFY_APP_NOT_CONFIGURED, e.getResponseEnum());
        }
    }

    // ==================== landing ====================

    @Nested
    @DisplayName("handleLanding")
    class Landing {

        @Test
        @DisplayName("HMAC 不符 → status=invalid，不签发票据")
        void shouldRejectInvalidHmac() {
            Map<String, String[]> params = signed(Map.of("shop", "shop-a.myshopify.com", "timestamp", "1700000000"));
            params.put("hmac", new String[]{"0".repeat(64)});

            assertEquals("/shopify-app/landing.html?status=invalid", service.handleLanding(params));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("shop 格式不符（签名正确）→ status=invalid")
        void shouldRejectInvalidShop() {
            Map<String, String[]> params = signed(Map.of("shop", "evil.com", "timestamp", "1700000000"));

            assertEquals("/shopify-app/landing.html?status=invalid", service.handleLanding(params));
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("已连接（PUBLIC_APP + AUTHED）→ status=connected")
        void shouldShowConnected() {
            when(websiteStore.findByShopDomain("shop-a.myshopify.com"))
                    .thenReturn(Optional.of(publicAppWebsite(1L, COMPANY_ID)));

            String location = service.handleLanding(signed(Map.of("shop", "shop-a.myshopify.com", "timestamp", "1700000000")));

            assertEquals("/shopify-app/landing.html?status=connected", location);
            verify(ticketStore, never()).create(any(), any());
        }

        @Test
        @DisplayName("未连接 → 签发 STORE 票据（无租户）并跳转授权 URL")
        void shouldIssueStoreTicket() {
            String location = service.handleLanding(signed(Map.of("shop", "shop-a.myshopify.com",
                    "timestamp", "1700000000", "host", "YWRtaW4uc2hvcGlmeS5jb20vc3RvcmUvc2hvcC1h")));

            URI uri = URI.create(location);
            assertEquals("shop-a.myshopify.com", uri.getHost());
            assertEquals(NONCE, parseQuery(uri.getRawQuery()).get("state"));
            ArgumentCaptor<ShopifyInstallTicket> ticket = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).create(ticket.capture(), eq(Duration.ofMinutes(10)));
            assertEquals(TicketType.STORE, ticket.getValue().type());
            assertEquals("shop-a", ticket.getValue().handle());
            assertNull(ticket.getValue().companyId());
            assertNull(ticket.getValue().userId());
        }

        @Test
        @DisplayName("记录已断开 → 重新签发票据")
        void shouldReinstallDisconnected() {
            ThirdPartyWebsite website = publicAppWebsite(1L, COMPANY_ID);
            website.setAuthStatus(ThirdPartyAuthStatusEnum.DISCONNECTED);
            when(websiteStore.findByShopDomain("shop-a.myshopify.com")).thenReturn(Optional.of(website));

            String location = service.handleLanding(signed(Map.of("shop", "shop-a.myshopify.com", "timestamp", "1700000000")));

            assertTrue(location.startsWith("https://shop-a.myshopify.com/admin/oauth/authorize?"));
        }

        @Test
        @DisplayName("未配置 → status=error&code=APP_NOT_CONFIGURED")
        void shouldReportNotConfigured() {
            properties.setClientId(null);

            assertEquals("/shopify-app/landing.html?status=error&code=APP_NOT_CONFIGURED",
                    service.handleLanding(signed(Map.of("shop", "shop-a.myshopify.com"))));
        }
    }

    // ==================== callback ====================

    @Nested
    @DisplayName("handleCallback")
    class Callback {

        @Test
        @DisplayName("state 票据不存在 → 结果页 invalid，不换 token")
        void shouldRejectUnknownState() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.empty());

            assertEquals("/shopify-app/landing.html?status=invalid", service.handleCallback(callbackParams("shop-a")));
            verifyNoInteractions(tokenService);
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN 票据 + HMAC 不符 → 回后台 INVALID_HMAC，删票据，不换 token、不写库")
        void shouldRejectAdminInvalidHmac() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            Map<String, String[]> params = callbackParams("shop-a");
            params.put("hmac", new String[]{"f".repeat(64)});

            String location = service.handleCallback(params);

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=INVALID_HMAC&handle=shop-a", location);
            verify(ticketStore).delete(NONCE);
            verifyNoInteractions(tokenService);
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN 票据 + shop 格式不符 → INVALID_SHOP")
        void shouldRejectAdminInvalidShop() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("code", AUTH_CODE);
            raw.put("shop", "shop-a.evil.com");
            raw.put("state", NONCE);
            raw.put("timestamp", "1700000000");

            String location = service.handleCallback(signed(raw));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=INVALID_SHOP&handle=shop-a", location);
            verifyNoInteractions(tokenService);
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN 票据 handle 与 shop 不一致 → INVALID_STATE")
        void shouldRejectHandleMismatch() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));

            String location = service.handleCallback(callbackParams("shop-b"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=INVALID_STATE&handle=shop-a", location);
            verifyNoInteractions(tokenService);
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("STORE 票据 + HMAC 不符 → 结果页 invalid")
        void shouldRejectStoreInvalidHmac() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", null)));
            Map<String, String[]> params = callbackParams("shop-a");
            params.put("hmac", new String[]{"f".repeat(64)});

            assertEquals("/shopify-app/landing.html?status=invalid", service.handleCallback(params));
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("STORE 票据已换过 token（回调重放）→ 结果页 invalid")
        void shouldRejectReplayedStoreTicket() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", token())));

            assertEquals("/shopify-app/landing.html?status=invalid", service.handleCallback(callbackParams("shop-a")));
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("换 token 失败 → ADMIN 回后台 TOKEN_EXCHANGE_FAILED，不写库")
        void shouldReportTokenExchangeFailure() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE))
                    .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "bad code " + AUTH_CODE));

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=TOKEN_EXCHANGE_FAILED&handle=shop-a", location);
            verify(ticketStore).delete(NONCE);
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("换 token 失败 → STORE 结果页 error&code=TOKEN_EXCHANGE_FAILED")
        void shouldReportStoreTokenExchangeFailure() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", null)));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenThrow(new IllegalStateException("x"));

            assertEquals("/shopify-app/landing.html?status=error&code=TOKEN_EXCHANGE_FAILED",
                    service.handleCallback(callbackParams("shop-a")));
        }

        @Test
        @DisplayName("ADMIN 成功（新建）：owner/租户来自票据，lastSyncOrderTime=now，删票据，302 success；租户上下文写库时生效、结束后清理")
        void shouldCreateWebsiteOnAdminSuccess() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenReturn(token());
            AtomicReference<Long> tenantDuringSave = new AtomicReference<>();
            when(websiteService.save(any(ThirdPartyWebsite.class))).thenAnswer(inv -> {
                tenantDuringSave.set(TenantContext.getCurrentTenant());
                ThirdPartyWebsite w = inv.getArgument(0);
                w.setId(900L);
                return w;
            });
            LocalDateTime before = LocalDateTime.now();

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=success&handle=shop-a", location);
            assertEquals(COMPANY_ID, tenantDuringSave.get());
            assertNull(TenantContext.getCurrentTenant());

            ArgumentCaptor<ThirdPartyWebsite> captor = ArgumentCaptor.forClass(ThirdPartyWebsite.class);
            verify(websiteService).save(captor.capture());
            ThirdPartyWebsite w = captor.getValue();
            assertEquals(WebsiteTypeEnum.SHOPIFY, w.getWebsiteType());
            assertEquals(ShopifyAccessMode.PUBLIC_APP, w.getAccessMode());
            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, w.getAuthStatus());
            assertEquals(StatusEnum.VALID, w.getStatus());
            assertEquals(COMPANY_ID, w.getCompanyId());
            assertEquals(USER_ID, w.getOwner().getId());
            assertEquals("店铺A", w.getNickName());
            assertEquals("shop-a", w.getHandle());
            assertEquals(CurrencyMode.PRESENTMENT_MONEY, w.getCurrencyMode());
            assertEquals(ACCESS_TOKEN, w.getToken());
            assertEquals(REFRESH_TOKEN, w.getRefreshToken());
            assertNotNull(w.getTokenExpiresAt());
            assertNotNull(w.getRefreshTokenExpiresAt());
            assertEquals("read_orders,read_all_orders", w.getGrantedScopes());
            assertTrue(w.getHasAllOrdersScope());
            assertFalse(w.getLastSyncOrderTime().isBefore(before));
            assertFalse(w.getLastSyncTime().isBefore(before));
            assertFalse(w.getInstalledAt().isBefore(before));
            assertNull(w.getClientId());
            verify(ticketStore).delete(NONCE);
            verifyNoInteractions(graphqlClient);
        }

        @Test
        @DisplayName("ADMIN 成功（恢复）：保留 lastSyncOrderTime，lastReconcileTime = uninstalledAt - 回看，owner 更新")
        void shouldRestoreDisconnectedWebsite() {
            LocalDateTime lastSyncOrderTime = LocalDateTime.of(2026, 9, 1, 10, 0);
            LocalDateTime uninstalledAt = LocalDateTime.of(2026, 10, 1, 12, 0);
            ThirdPartyWebsite existing = publicAppWebsite(55L, COMPANY_ID);
            existing.setAuthStatus(ThirdPartyAuthStatusEnum.DISCONNECTED);
            existing.setStatus(StatusEnum.INVALID);
            existing.setToken("DISCONNECTED:55");
            existing.setOwner(SystemUser.builder().id(1L).build());
            existing.setLastSyncOrderTime(lastSyncOrderTime);
            existing.setUninstalledAt(uninstalledAt);
            existing.setNickName("原名称");
            existing.setCurrencyMode(CurrencyMode.SHOP_MONEY);
            when(websiteStore.findByShopDomain("shop-a.myshopify.com")).thenReturn(Optional.of(existing));
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenReturn(token());
            LocalDateTime before = LocalDateTime.now();

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=success&handle=shop-a", location);
            ArgumentCaptor<ThirdPartyWebsite> captor = ArgumentCaptor.forClass(ThirdPartyWebsite.class);
            verify(websiteService).save(captor.capture());
            ThirdPartyWebsite w = captor.getValue();
            assertSame(existing, w);
            assertEquals(55L, w.getId());
            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, w.getAuthStatus());
            assertEquals(StatusEnum.VALID, w.getStatus());
            assertEquals(USER_ID, w.getOwner().getId());
            assertEquals(ACCESS_TOKEN, w.getToken());
            assertEquals(lastSyncOrderTime, w.getLastSyncOrderTime());
            assertEquals(uninstalledAt.minusMinutes(30), w.getLastReconcileTime());
            assertNull(w.getUninstalledAt());
            assertFalse(w.getInstalledAt().isBefore(before));
            assertEquals("店铺A", w.getNickName());
            verify(ticketStore).delete(NONCE);
        }

        @Test
        @DisplayName("ADMIN：handle 属于其他租户 → HANDLE_TAKEN，不写库")
        void shouldReportHandleTaken() {
            when(websiteStore.findByShopDomain("shop-a.myshopify.com"))
                    .thenReturn(Optional.of(publicAppWebsite(55L, OTHER_COMPANY_ID)));
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenReturn(token());

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=HANDLE_TAKEN&handle=shop-a", location);
            verify(websiteService, never()).save(any());
            verify(ticketStore).delete(NONCE);
            assertNull(TenantContext.getCurrentTenant());
        }

        @Test
        @DisplayName("ADMIN：写库唯一键冲突 → HANDLE_TAKEN")
        void shouldMapUniqueViolationToHandleTaken() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenReturn(token());
            when(websiteService.save(any(ThirdPartyWebsite.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry '" + ACCESS_TOKEN + "'"));

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals(RETURN_ORIGIN + "/#/shopify-bind?status=error&code=HANDLE_TAKEN&handle=shop-a", location);
        }

        @Test
        @DisplayName("STORE 成功：token 写入票据并续期为 pending TTL，302 landing.html?ticket=")
        void shouldStorePendingTokenOnStoreSuccess() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", null)));
            ShopifyTokenService.ExchangedToken token = token();
            when(tokenService.exchangeAuthorizationCode("shop-a", AUTH_CODE)).thenReturn(token);

            String location = service.handleCallback(callbackParams("shop-a"));

            assertEquals("/shopify-app/landing.html?ticket=" + NONCE, location);
            ArgumentCaptor<ShopifyInstallTicket> captor = ArgumentCaptor.forClass(ShopifyInstallTicket.class);
            verify(ticketStore).update(captor.capture(), eq(Duration.ofMinutes(30)));
            assertEquals(NONCE, captor.getValue().nonce());
            assertEquals(token, captor.getValue().pendingToken());
            verify(ticketStore, never()).delete(any());
            verify(websiteService, never()).save(any());
        }
    }

    // ==================== resolve ====================

    @Nested
    @DisplayName("resolveLanding")
    class Resolve {

        @Test
        @DisplayName("票据不存在 / 未换 token / 非 STORE → TICKET_EXPIRED")
        void shouldRejectExpiredTicket() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.empty());
            assertEquals(ErrorCode.TICKET_EXPIRED,
                    assertThrows(InstallException.class, () -> service.resolveLanding(NONCE, "admin.example.com")).getCode());

            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", null)));
            assertEquals(ErrorCode.TICKET_EXPIRED,
                    assertThrows(InstallException.class, () -> service.resolveLanding(NONCE, "admin.example.com")).getCode());

            when(ticketStore.get(NONCE)).thenReturn(Optional.of(adminTicket("shop-a")));
            assertEquals(ErrorCode.TICKET_EXPIRED,
                    assertThrows(InstallException.class, () -> service.resolveLanding(NONCE, "admin.example.com")).getCode());
        }

        @Test
        @DisplayName("域名解析不到公司或格式非法 → UNKNOWN_HOST")
        void shouldRejectUnknownHost() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", token())));

            for (String host : List.of("admin.unknown.com", "example.com", "admin.example.com:8443",
                    "https://user@admin.example.com", "localhost", "", "not a host")) {
                InstallException e = assertThrows(InstallException.class, () -> service.resolveLanding(NONCE, host), host);
                assertEquals(ErrorCode.UNKNOWN_HOST, e.getCode(), host);
            }
        }

        @Test
        @DisplayName("成功：返回 https://{host}/#/shopify-bind?bindTicket=（参数名不是 ticket），不消费票据")
        void shouldResolveAdminHost() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", token())));

            String url = service.resolveLanding(NONCE, " https://Admin.Example.com/#/login?x=1 ");

            assertEquals("https://admin.example.com/#/shopify-bind?bindTicket=" + NONCE, url);
            assertFalse(url.contains("?ticket="));
            verify(companyService).identityCached("example.com");
            verify(ticketStore, never()).delete(any());
            verify(ticketStore, never()).update(any(), any());
        }
    }

    // ==================== bind ====================

    @Nested
    @DisplayName("bind")
    class Bind {

        @Test
        @DisplayName("票据不存在或未换 token → 票据不存在或已过期")
        void shouldRejectInvalidTicket() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", null)));

            BaseException e = assertThrows(BaseException.class, () -> service.bind(NONCE, null, null, COMPANY_ID, USER_ID));
            assertEquals(ClientResponseEnum.PARAMETER_ILLEGAL, e.getResponseEnum());
            assertTrue(e.getMessage().contains("票据不存在或已过期"));
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("成功：当前租户与登录人下新建 PUBLIC_APP 店铺并删除票据")
        void shouldBindStore() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", token())));

            ThirdPartyWebsite saved = service.bind(NONCE, null, null, COMPANY_ID, USER_ID);

            assertEquals(900L, saved.getId());
            assertEquals(ShopifyAccessMode.PUBLIC_APP, saved.getAccessMode());
            assertEquals(COMPANY_ID, saved.getCompanyId());
            assertEquals(USER_ID, saved.getOwner().getId());
            assertEquals("shop-a", saved.getNickName());
            assertEquals(CurrencyMode.SHOP_MONEY, saved.getCurrencyMode());
            assertEquals(ACCESS_TOKEN, saved.getToken());
            assertNotNull(saved.getLastSyncOrderTime());
            verify(ticketStore).delete(NONCE);
        }

        @Test
        @DisplayName("handle 被占用 → Handle已被占用，保留票据")
        void shouldRejectTakenHandle() {
            when(ticketStore.get(NONCE)).thenReturn(Optional.of(storeTicket("shop-a", token())));
            ThirdPartyWebsite credentials = publicAppWebsite(55L, COMPANY_ID);
            credentials.setAccessMode(ShopifyAccessMode.CREDENTIALS);
            when(websiteStore.findByShopDomain("shop-a.myshopify.com")).thenReturn(Optional.of(credentials));

            BaseException e = assertThrows(BaseException.class, () -> service.bind(NONCE, "A", null, COMPANY_ID, USER_ID));
            assertTrue(e.getMessage().contains("Handle已被占用"));
            verify(websiteService, never()).save(any());
            verify(ticketStore, never()).delete(any());
        }
    }

    // ==================== webhook 注册 ====================

    @Nested
    @DisplayName("upsert webhook 注册")
    class WebhookRegistration {

        @Test
        @DisplayName("开关关闭 → 不调用 GraphQL")
        void shouldSkipWhenDisabled() {
            service.upsert("shop-a", COMPANY_ID, USER_ID, "A", null, token());

            verifyNoInteractions(graphqlClient);
        }

        @Test
        @DisplayName("开关打开 → 先查重，只为缺失的 topic 调用 webhookSubscriptionCreate")
        void shouldRegisterMissingTopics() {
            properties.setRegisterWebhooksOnInstall(true);
            String callbackUrl = APP_HOST + "/shopify-app/webhooks";
            JSONObject listData = JSONUtil.parseObj("{\"webhookSubscriptions\":{\"nodes\":["
                    + "{\"id\":\"gid://1\",\"topic\":\"ORDERS_CREATE\",\"uri\":\"" + callbackUrl + "\"},"
                    + "{\"id\":\"gid://2\",\"topic\":\"ORDERS_UPDATED\",\"uri\":\"https://other.example.com/hook\"}]}}");
            when(graphqlClient.execute(any(ThirdPartyWebsiteDto.class), eq(ShopifyInstallService.WEBHOOK_LIST_QUERY)))
                    .thenReturn(new ShopifyGraphqlClient.GraphqlResult(listData, null, null));
            JSONObject createData = JSONUtil.parseObj("{\"webhookSubscriptionCreate\":{\"webhookSubscription\":{\"id\":\"gid://3\"},\"userErrors\":[]}}");
            when(graphqlClient.execute(any(ThirdPartyWebsiteDto.class), eq(ShopifyInstallService.WEBHOOK_CREATE_MUTATION), anyMap()))
                    .thenReturn(new ShopifyGraphqlClient.GraphqlResult(createData, null, null));

            service.upsert("shop-a", COMPANY_ID, USER_ID, "A", null, token());

            ArgumentCaptor<ThirdPartyWebsiteDto> dto = ArgumentCaptor.forClass(ThirdPartyWebsiteDto.class);
            verify(graphqlClient).execute(dto.capture(), eq(ShopifyInstallService.WEBHOOK_LIST_QUERY));
            assertEquals("shop-a", dto.getValue().getHandle());
            assertTrue(dto.getValue().isPublicApp());
            assertEquals(ACCESS_TOKEN, dto.getValue().getToken());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
            verify(graphqlClient, times(3)).execute(any(ThirdPartyWebsiteDto.class),
                    eq(ShopifyInstallService.WEBHOOK_CREATE_MUTATION), variables.capture());
            List<String> topics = new ArrayList<>();
            for (Map<String, Object> v : variables.getAllValues()) {
                topics.add((String) v.get("topic"));
                @SuppressWarnings("unchecked")
                Map<String, Object> subscription = (Map<String, Object>) v.get("webhookSubscription");
                assertEquals(callbackUrl, subscription.get("uri"));
                assertEquals("JSON", subscription.get("format"));
            }
            assertEquals(List.of("ORDERS_UPDATED", "ORDERS_CANCELLED", "APP_UNINSTALLED"), topics);
        }

        @Test
        @DisplayName("注册失败只记日志，不影响绑定结果")
        void shouldIgnoreRegistrationFailure() {
            properties.setRegisterWebhooksOnInstall(true);
            when(graphqlClient.execute(any(ThirdPartyWebsiteDto.class), anyString()))
                    .thenThrow(new IllegalStateException("boom " + ACCESS_TOKEN));

            ThirdPartyWebsite saved = service.upsert("shop-a", COMPANY_ID, USER_ID, "A", null, token());

            assertEquals(900L, saved.getId());
            assertTrue(logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("webhook registration failed")));
        }
    }

    // ==================== helpers ====================

    private static Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }

    private static ThirdPartyWebsite publicAppWebsite(Long id, Long companyId) {
        ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                .handle("shop-a")
                .nickName("A")
                .websiteType(WebsiteTypeEnum.SHOPIFY)
                .accessMode(ShopifyAccessMode.PUBLIC_APP)
                .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                .token("old-token")
                .build();
        website.setId(id);
        website.setCompanyId(companyId);
        return website;
    }

    private static ShopifyTokenService.ExchangedToken token() {
        LocalDateTime now = LocalDateTime.now();
        return new ShopifyTokenService.ExchangedToken(ACCESS_TOKEN, now.plusHours(1), REFRESH_TOKEN, now.plusDays(90),
                "read_orders,read_all_orders");
    }

    private static ShopifyInstallTicket adminTicket(String handle) {
        return new ShopifyInstallTicket(NONCE, TicketType.ADMIN, handle, COMPANY_ID, USER_ID, "店铺A",
                CurrencyMode.PRESENTMENT_MONEY, null, RETURN_ORIGIN, null, LocalDateTime.now());
    }

    private static ShopifyInstallTicket storeTicket(String handle, ShopifyTokenService.ExchangedToken pendingToken) {
        return new ShopifyInstallTicket(NONCE, TicketType.STORE, handle, null, null, null, null, null, null,
                pendingToken, LocalDateTime.now());
    }

    private static Map<String, String[]> callbackParams(String handle) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("code", AUTH_CODE);
        raw.put("shop", handle + ".myshopify.com");
        raw.put("state", NONCE);
        raw.put("timestamp", "1700000000");
        return signed(raw);
    }

    /**
     * 按 Shopify 规则独立计算签名（键排序、k=v 用 & 连接、HMAC-SHA256 hex），由服务中的真实 ShopifyHmac 校验
     */
    private static Map<String, String[]> signed(Map<String, String> raw) {
        String message = new TreeMap<>(raw).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String hmac;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            hmac = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Map<String, String[]> params = new HashMap<>();
        raw.forEach((k, v) -> params.put(k, new String[]{v}));
        params.put("hmac", new String[]{hmac});
        return params;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            result.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return result;
    }
}
