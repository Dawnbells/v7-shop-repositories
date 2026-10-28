package cn.v7soft.admin.controller;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.controller.req.EditShopifyWebsiteRequest;
import cn.v7soft.admin.controller.req.ShopifyBindRequest;
import cn.v7soft.admin.controller.req.ShopifyInstallUrlRequest;
import cn.v7soft.admin.controller.resp.ShopifyInstallUrlResponse;
import cn.v7soft.admin.controller.resp.ThirdPartyWebsiteResponse;
import cn.v7soft.admin.service.IShopifyInstallService;
import cn.v7soft.admin.service.IShopifyInstallService.ErrorCode;
import cn.v7soft.admin.service.IShopifyInstallService.InstallException;
import cn.v7soft.admin.service.IShopifyInstallService.IntentCookie;
import cn.v7soft.admin.service.IShopifyInstallService.Redirect;
import cn.v7soft.admin.service.IShopifyOrderSyncService;
import cn.v7soft.admin.service.IThirdPartyWebsiteService;
import cn.v7soft.core.exception.BaseException;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.entities.primary.ThirdPartyWebsite;
import cn.v7soft.dao.enums.CurrencyMode;
import cn.v7soft.dao.enums.ShopifyAccessMode;
import cn.v7soft.dao.enums.ThirdPartyAuthStatusEnum;
import cn.v7soft.dao.enums.WebsiteTypeEnum;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.tenant.WebsiteContext;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopifyAppControllerTest {

    // ==================== 公开端点 /shopify-app ====================

    @Nested
    @DisplayName("ShopifyAppController")
    class PublicEndpoints {
        private IShopifyInstallService installService;
        private MockMvc mockMvc;

        private ShopifyAppProperties properties;

        @BeforeEach
        void setUp() {
            installService = mock(IShopifyInstallService.class);
            properties = new ShopifyAppProperties();
            properties.setAppHost("https://shopify.vantasite.test");
            mockMvc = MockMvcBuilders.standaloneSetup(new ShopifyAppController(installService, properties)).build();
        }

        @Test
        @DisplayName("GET /landing：把原始查询参数与意向 cookie 交给服务，302 到服务返回的地址，KEEP 时不写 cookie")
        void landingRedirects() throws Exception {
            when(installService.handleLanding(anyMap(), any()))
                    .thenReturn(Redirect.of("/shopify-app/landing.html?status=connected"));

            mockMvc.perform(get("/shopify-app/landing").param("shop", "shop-a.myshopify.com").param("hmac", "abc")
                            .param("timestamp", "1").cookie(new Cookie("v7_shopify_connect", "intent-1")))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "/shopify-app/landing.html?status=connected"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().doesNotExist("Set-Cookie"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String[]>> params = ArgumentCaptor.forClass(Map.class);
            verify(installService).handleLanding(params.capture(), eq("intent-1"));
            assertArrayEquals(new String[]{"shop-a.myshopify.com"}, params.getValue().get("shop"));
            assertArrayEquals(new String[]{"abc"}, params.getValue().get("hmac"));
        }

        @Test
        @DisplayName("GET /landing：服务要求 CLEAR → 写过期的意向 cookie")
        void landingClearsIntentCookie() throws Exception {
            when(installService.handleLanding(anyMap(), any())).thenReturn(
                    new Redirect("https://shop-a.myshopify.com/admin/oauth/authorize?x=1", IntentCookie.CLEAR, null));

            String setCookie = mockMvc.perform(get("/shopify-app/landing").param("shop", "shop-a.myshopify.com"))
                    .andExpect(status().isFound())
                    .andReturn().getResponse().getHeader("Set-Cookie");

            assertNotNull(setCookie);
            assertTrue(setCookie.startsWith("v7_shopify_connect=;"), setCookie);
            assertTrue(setCookie.contains("Max-Age=0"), setCookie);
            assertTrue(setCookie.contains("Path=/shopify-app/"), setCookie);
            verify(installService).handleLanding(anyMap(), isNull());
        }

        @Test
        @DisplayName("GET /connect：服务要求 SET → 写 HttpOnly/Secure/Lax 意向 cookie，有效期取 connect-intent-ttl-minutes")
        void connectSetsIntentCookie() throws Exception {
            when(installService.handleConnect("intent-1")).thenReturn(
                    new Redirect("https://apps.shopify.com/v7-order-connector", IntentCookie.SET, "intent-1"));

            String setCookie = mockMvc.perform(get("/shopify-app/connect").param("intent", "intent-1"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://apps.shopify.com/v7-order-connector"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getHeader("Set-Cookie");

            assertNotNull(setCookie);
            assertTrue(setCookie.startsWith("v7_shopify_connect=intent-1;"), setCookie);
            assertTrue(setCookie.contains("Max-Age=3600"), setCookie);
            assertTrue(setCookie.contains("Path=/shopify-app/"), setCookie);
            assertTrue(setCookie.contains("HttpOnly"), setCookie);
            assertTrue(setCookie.contains("Secure"), setCookie);
            assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        }

        @Test
        @DisplayName("GET /connect：app-host 为 http（本地调试）→ cookie 不带 Secure")
        void connectCookieWithoutSecureOnHttp() throws Exception {
            properties.setAppHost("http://localhost:8080");
            when(installService.handleConnect(any())).thenReturn(
                    new Redirect("/shopify-app/landing.html?status=install", IntentCookie.SET, "intent-1"));

            String setCookie = mockMvc.perform(get("/shopify-app/connect").param("intent", "intent-1"))
                    .andReturn().getResponse().getHeader("Set-Cookie");

            assertNotNull(setCookie);
            assertFalse(setCookie.contains("Secure"), setCookie);
        }

        @Test
        @DisplayName("GET /oauth/callback：302 到服务返回的地址（可为外部后台域名）")
        void callbackRedirects() throws Exception {
            String target = "https://admin.example.com/#/shopify-bind?status=success&handle=shop-a";
            when(installService.handleCallback(anyMap())).thenReturn(target);

            mockMvc.perform(get("/shopify-app/oauth/callback").param("code", "c").param("state", "s"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", target));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String[]>> params = ArgumentCaptor.forClass(Map.class);
            verify(installService).handleCallback(params.capture());
            assertArrayEquals(new String[]{"s"}, params.getValue().get("state"));
        }

        @Test
        @DisplayName("POST /landing/resolve 成功：200 {redirectUrl}（bindTicket）")
        void resolveSuccess() throws Exception {
            String url = "https://admin.example.com/#/shopify-bind?bindTicket=" + "a".repeat(64);
            when(installService.resolveLanding("t1", "admin.example.com")).thenReturn(url);

            mockMvc.perform(post("/shopify-app/landing/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticket\":\"t1\",\"adminHost\":\"admin.example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.redirectUrl").value(url));
        }

        @Test
        @DisplayName("POST /landing/resolve 票据过期：400 {code: TICKET_EXPIRED}")
        void resolveTicketExpired() throws Exception {
            when(installService.resolveLanding(any(), any())).thenThrow(new InstallException(ErrorCode.TICKET_EXPIRED));

            mockMvc.perform(post("/shopify-app/landing/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticket\":\"t1\",\"adminHost\":\"admin.example.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TICKET_EXPIRED"));
        }

        @Test
        @DisplayName("POST /landing/resolve 未知域名：400 {code: UNKNOWN_HOST}")
        void resolveUnknownHost() throws Exception {
            when(installService.resolveLanding(any(), any())).thenThrow(new InstallException(ErrorCode.UNKNOWN_HOST));

            mockMvc.perform(post("/shopify-app/landing/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticket\":\"t1\",\"adminHost\":\"admin.unknown.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_HOST"));
        }
    }

    // ==================== 后台接口 /third-party-website/shopify ====================

    @Nested
    @DisplayName("ShopifyWebsiteController")
    class AdminEndpoints {
        private IThirdPartyWebsiteService websiteService;
        private IShopifyOrderSyncService orderSyncService;
        private IShopifyInstallService installService;
        private ShopifyWebsiteController controller;
        private MockedStatic<StpUtil> stpUtil;
        private MockedStatic<SaSessionUtil> saSessionUtil;

        @BeforeEach
        void setUp() {
            websiteService = mock(IThirdPartyWebsiteService.class);
            orderSyncService = mock(IShopifyOrderSyncService.class);
            installService = mock(IShopifyInstallService.class);
            controller = new ShopifyWebsiteController(websiteService, orderSyncService, installService);
            stpUtil = mockStatic(StpUtil.class);
            saSessionUtil = mockStatic(SaSessionUtil.class);
            saSessionUtil.when(SaSessionUtil::getLoginUser).thenReturn(SystemUserDto.builder().id("501").build());
            Company company = new Company();
            company.setId(7L);
            TenantContext.setCurrentTenant(7L, company);
            when(websiteService.save(any(ThirdPartyWebsite.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @AfterEach
        void tearDown() {
            stpUtil.close();
            saSessionUtil.close();
            TenantContext.clear();
            WebsiteContext.clear();
        }

        @Test
        @DisplayName("bind：无权限 → 抛出 NotPermissionException，不调用服务")
        void bindRequiresPermission() {
            stpUtil.when(() -> StpUtil.checkPermission("third-party-website.create"))
                    .thenThrow(new NotPermissionException("third-party-website.create"));
            ShopifyBindRequest request = new ShopifyBindRequest();
            request.setTicket("t1");

            assertThrows(NotPermissionException.class, () -> controller.bind(request));
            verifyNoInteractions(installService);
        }

        @Test
        @DisplayName("bind：校验 create 权限，按当前租户与登录人绑定并返回店铺")
        void bindUsesCurrentTenantAndUser() {
            ThirdPartyWebsite saved = publicAppWebsite();
            when(installService.bind("t1", "A", CurrencyMode.SHOP_MONEY, 7L, 501L)).thenReturn(saved);
            ShopifyBindRequest request = new ShopifyBindRequest();
            request.setTicket("t1");
            request.setNickName("A");
            request.setCurrencyMode(CurrencyMode.SHOP_MONEY);

            ThirdPartyWebsiteResponse response = controller.bind(request);

            stpUtil.verify(() -> StpUtil.checkPermission("third-party-website.create"));
            assertEquals("55", response.getId());
            assertEquals(ShopifyAccessMode.PUBLIC_APP, response.getAccessMode());
            assertNotNull(response.getCompactId());
            assertEquals("", response.getToken());
        }

        @Test
        @DisplayName("install-url：新建校验 create，回跳 origin 取 Origin 头，顶级域名经 DomainUtils 解析")
        void installUrlForNewStore() {
            when(installService.createAdminInstallUrl(isNull(), eq("A"), isNull(),
                    eq("https://admin.example.com"), eq("example.com"), eq(7L), eq(501L))).thenReturn("https://auth");
            ShopifyInstallUrlRequest request = new ShopifyInstallUrlRequest();
            request.setNickName("A");
            MockHttpServletRequest http = new MockHttpServletRequest();
            http.addHeader("Origin", "https://admin.example.com");

            ShopifyInstallUrlResponse response = controller.installUrl(request, http);

            assertEquals("https://auth", response.getAuthorizeUrl());
            stpUtil.verify(() -> StpUtil.checkPermission("third-party-website.create"));
        }

        @Test
        @DisplayName("install-url：带 id 重连校验 update 权限；无 Origin 时取 Referer")
        void installUrlForReconnect() {
            when(installService.createAdminInstallUrl(eq(55L), any(), any(),
                    eq("https://admin.example.com/#/website/third-party"), eq("example.com"), eq(7L), eq(501L)))
                    .thenReturn("https://auth");
            ShopifyInstallUrlRequest request = new ShopifyInstallUrlRequest();
            request.setId("55");
            MockHttpServletRequest http = new MockHttpServletRequest();
            http.addHeader("Referer", "https://admin.example.com/#/website/third-party");

            assertEquals("https://auth", controller.installUrl(request, http).getAuthorizeUrl());
            stpUtil.verify(() -> StpUtil.checkPermission("third-party-website.update"));
        }

        @Test
        @DisplayName("doEdit：PUBLIC_APP 记录只更新名称与币种，不验证凭证、不改 handle/clientId/secret")
        void doEditPublicAppOnlyUpdatesNameAndCurrency() {
            ThirdPartyWebsite existing = publicAppWebsite();
            when(websiteService.getById(55L)).thenReturn(existing);
            EditShopifyWebsiteRequest request = new EditShopifyWebsiteRequest();
            request.setId("55");
            request.setNickName(" 新名称 ");
            request.setHandle("other-shop");
            request.setClientId("cid");
            request.setClientSecret("secret");
            request.setCurrencyMode(CurrencyMode.PRESENTMENT_MONEY);

            ThirdPartyWebsiteResponse response = controller.doEdit(request);

            assertEquals("新名称", existing.getNickName());
            assertEquals(CurrencyMode.PRESENTMENT_MONEY, existing.getCurrencyMode());
            assertEquals("shop-a", existing.getHandle());
            assertNull(existing.getClientId());
            assertNull(existing.getClientSecret());
            assertEquals(ThirdPartyAuthStatusEnum.AUTHED, existing.getAuthStatus());
            verify(orderSyncService, never()).verifyAndUpdateAuthStatus(any());
            assertEquals("55", response.getId());
            stpUtil.verify(() -> StpUtil.checkPermission("third-party-website.update"));
        }

        @Test
        @DisplayName("doEdit：凭证模式缺 Client ID → PARAMETER_ILLEGAL")
        void doEditCredentialsRequiresClientId() {
            when(websiteService.getByHandle("shop-a")).thenReturn(java.util.Optional.empty());
            EditShopifyWebsiteRequest request = new EditShopifyWebsiteRequest();
            request.setNickName("A");
            request.setHandle("shop-a");
            request.setClientSecret("secret");

            BaseException e = assertThrows(BaseException.class, () -> controller.doEdit(request));
            assertTrue(e.getMessage().contains("Client ID不能为空"));
            verify(websiteService, never()).save(any());
        }

        @Test
        @DisplayName("doEdit：凭证模式新增保持原行为（验证凭证），accessMode=CREDENTIALS")
        void doEditCredentialsCreate() {
            when(websiteService.getByHandle("shop-a")).thenReturn(java.util.Optional.empty());
            when(websiteService.save(any(ThirdPartyWebsite.class))).thenAnswer(inv -> {
                ThirdPartyWebsite w = inv.getArgument(0);
                w.setId(66L);
                return w;
            });
            EditShopifyWebsiteRequest request = new EditShopifyWebsiteRequest();
            request.setNickName("A");
            request.setHandle("shop-a");
            request.setClientId(" cid ");
            request.setClientSecret("secret");

            ThirdPartyWebsiteResponse response = controller.doEdit(request);

            ArgumentCaptor<ThirdPartyWebsite> captor = ArgumentCaptor.forClass(ThirdPartyWebsite.class);
            verify(orderSyncService).verifyAndUpdateAuthStatus(captor.capture());
            ThirdPartyWebsite website = captor.getValue();
            assertEquals("cid", website.getClientId());
            assertEquals(ShopifyAccessMode.CREDENTIALS, website.getAccessMode());
            assertEquals(ThirdPartyAuthStatusEnum.INIT, website.getAuthStatus());
            assertEquals(ShopifyAccessMode.CREDENTIALS, response.getAccessMode());
            stpUtil.verify(() -> StpUtil.checkPermission("third-party-website.create"));
        }

        private ThirdPartyWebsite publicAppWebsite() {
            ThirdPartyWebsite website = ThirdPartyWebsite.builder()
                    .handle("shop-a")
                    .nickName("A")
                    .websiteType(WebsiteTypeEnum.SHOPIFY)
                    .accessMode(ShopifyAccessMode.PUBLIC_APP)
                    .authStatus(ThirdPartyAuthStatusEnum.AUTHED)
                    .token("token")
                    .build();
            website.setId(55L);
            website.setCompanyId(7L);
            return website;
        }
    }
}
