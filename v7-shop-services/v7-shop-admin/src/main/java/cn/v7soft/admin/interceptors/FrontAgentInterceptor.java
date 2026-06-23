package cn.v7soft.admin.interceptors;

import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.admin.service.frontagent.FrontAgentProperties;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 前端机 agent 接口（/front-agent/**）专用拦截器，做两件事：
 * <ol>
 * <li><b>静态 Bearer token 鉴权</b>——HTTPS 只认证服务器方向，token 负责认证调用方；
 *     本通道分发证书私钥，绝不可匿名（设计文档 §4.4「鉴权——token 不可省」）。</li>
 * <li><b>按请求 Host 解析公司并设置租户</b>——agent 用各公司管理后台域名调用，
 *     租户隔离保证 A 公司的 token + 域名取不到 B 公司的域名与私钥。</li>
 * </ol>
 * 注意：CompanyTenantInterceptor 已跳过本路径（它按 Origin/Referer 解析域名，
 * 不适用于无浏览器头的服务端调用），本拦截器以 order=100 注册在其后——
 * 否则它 preHandle 开头的 TenantContext.clear() 会清掉这里设置的租户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrontAgentInterceptor implements HandlerInterceptor {

    private final FrontAgentProperties properties;
    private final ICompanyService companyService;

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        List<String> tokens = properties.getTokens() == null ? List.of()
                : properties.getTokens().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (tokens.isEmpty()) {
            // 未配置 token = 功能未启用：宁可不可用，不可裸奔
            log.warn("front-agent 接口未配置 FRONT_AGENT_TOKENS，拒绝来自 {} 的请求", clientIp(request));
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED);
        }

        String token = extractBearerToken(request);
        if (token == null || !matchesAnyToken(token, tokens)) {
            // 失败鉴权必须记录来源 IP：用于发现 token 扫描/泄露（设计文档 §4.4 服务端加固）
            log.warn("front-agent 鉴权失败: ip={}, path={}", clientIp(request), request.getRequestURI());
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED);
        }

        String topLevelDomain = topLevelDomainOf(request.getServerName());
        Company company = topLevelDomain == null ? null : companyService.identityCached(topLevelDomain);
        if (company == null) {
            log.warn("front-agent 无法按 Host 识别公司: host={}, ip={}", request.getServerName(), clientIp(request));
            return reject(response, HttpServletResponse.SC_FORBIDDEN);
        }
        TenantContext.setCurrentTenant(company.getId(), company);
        return true;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                @NotNull Object handler, Exception ex) {
        TenantContext.clear();
    }

    /**
     * 从 Authorization: Bearer &lt;token&gt; 头提取 token；缺失/格式不符返回 null
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 恒定时间比较，防时序侧信道逐字节猜 token。
     * 故意不短路：对配置的每个 token 都执行一次 MessageDigest.isEqual。
     */
    private boolean matchesAnyToken(String candidate, List<String> tokens) {
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        boolean matched = false;
        for (String configured : tokens) {
            matched |= MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), candidateBytes);
        }
        return matched;
    }

    /**
     * 取 Host 的后两段作为一级域名（admin.xyz.com → xyz.com）。
     * IP 或单段主机名原样返回（identityCached 查不到则被 403 挡下）。
     */
    private String topLevelDomainOf(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length < 2 || parts[parts.length - 1].chars().allMatch(Character::isDigit)) {
            // 单段主机名，或最后一段是纯数字（IPv4）
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    /**
     * 优先取 X-Forwarded-For 首个地址（经过反代时为真实来源），否则取直连地址
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean reject(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
        return false;
    }
}
