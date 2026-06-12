package cn.v7soft.admin.interceptors;

import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.frontagent.FrontAgentProperties;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrontAgentInterceptorTest {

    @Mock
    ICompanyService companyService;

    private FrontAgentProperties properties;
    private FrontAgentInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new FrontAgentProperties();
        interceptor = new FrontAgentInterceptor(properties, companyService);
        request = new MockHttpServletRequest("GET", "/front-agent/manifest");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("未配置 token → 一律 401（接口禁用，宁可不可用不可裸奔）")
    void rejectsWhenNoTokenConfigured() throws Exception {
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("缺少/错误的 Bearer token → 401")
    void rejectsWrongToken() throws Exception {
        properties.setTokens(List.of("right-token"));

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());

        request.addHeader("Authorization", "Bearer wrong-token");
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("token 正确 + Host 可识别公司 → 放行并设置租户；afterCompletion 清理")
    void allowsValidTokenAndResolvesTenantByHost() throws Exception {
        properties.setTokens(List.of("secret-token"));
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(7L);
        when(companyService.identityCached("xyz-example.com")).thenReturn(company);

        request.addHeader("Authorization", "Bearer secret-token");
        request.setServerName("admin.xyz-example.com");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(7L, TenantContext.getCurrentTenant(), "租户必须是 Host 后两段解析出的公司");

        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(TenantContext.getCurrentTenant(), "afterCompletion 必须清理租户上下文");
    }

    @Test
    @DisplayName("多 token 并存（轮换窗口）：第二个 token 也能通过")
    void allowsSecondTokenDuringRotation() throws Exception {
        properties.setTokens(List.of("old-token", "new-token"));
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(7L);
        when(companyService.identityCached("xyz-example.com")).thenReturn(company);

        request.addHeader("Authorization", "Bearer new-token");
        request.setServerName("admin.xyz-example.com");

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    @DisplayName("Host 无法识别公司 → 403（token 对了也不行，杜绝跨公司访问）")
    void rejectsUnknownHost() throws Exception {
        properties.setTokens(List.of("secret-token"));
        when(companyService.identityCached("unknown.com")).thenReturn(null);

        request.addHeader("Authorization", "Bearer secret-token");
        request.setServerName("admin.unknown.com");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertNull(TenantContext.getCurrentTenant());
    }
}
