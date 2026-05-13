package cn.v7soft.admin.interceptors;

import java.time.LocalDateTime;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.common.constants.StpSessionKey;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.common.utils.DomainUtils;
import cn.v7soft.dao.utils.SaSessionUtil;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.tenant.TenantContext;
import cn.v7soft.dao.tenant.WebsiteContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
public class CompanyTenantInterceptor implements HandlerInterceptor {
    private final ICompanyService companyService;

    public CompanyTenantInterceptor(ICompanyService companyService) {
        log.debug("CompanyTenantFilter construct");
        this.companyService = companyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        // 记录请求信息
//        log.info("Filter called for URL: {} at {} by thread {}， process={}", request.getRequestURI(), LocalDateTime.now(), Thread.currentThread().getId(), ProcessHandle.current().pid());
        TenantContext.clear();
        WebsiteContext.clear();

        String path = request.getRequestURI();

        if(path == null || path.endsWith(".php")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Forbidden!!");
            return false;
        }
        if (path.startsWith("/webjars/") || path.startsWith("/v3/")
                || path.startsWith("/company/") || path.startsWith("/favicon.ico")
                || path.startsWith("/error") || path.startsWith("/risk/")
                || path.startsWith("/doc.html") || path.startsWith("/multimedia/")
                || path.startsWith("/turboflow-bridge/")) {
            // 如果请求的是 /webjars/ 或 /v3/ 路径，直接跳过此过滤器
            return true;
        }

        if (path.startsWith("/shopline/") || "/temporary-order/sync".equals(path)) {
            return true;
        }

        String topLevelDomain = DomainUtils.getOriginTopLevelDomain(request);
//        log.debug("top level domain is {}, path = {}, Website ID = {}, isWebsiteAdmin = {}", topLevelDomain, path, WebsiteContext.getCurrentWebsiteId(), WebsiteContext.isWebsiteAdmin());
        Company company = this.companyService.identityCached(topLevelDomain);
        if (company == null) {
            log.debug("Can not match company: {}", topLevelDomain);
            return false;
        }
        TenantContext.setCurrentTenant(company.getId(), company);
        if (StpUtil.isLogin()) {
            SystemUserDto systemUser = SaSessionUtil.getLoginUser();
            if (SystemUserType.ADMIN == systemUser.getUserType()) {
                TenantContext.silent();
            }
            StpUtil.getSession().set(StpSessionKey.COMPANY_IDENTITY, company);
        }
        return true;
    }

    @Override
    public void postHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
        TenantContext.clear();
        WebsiteContext.clear();
    }
    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
        TenantContext.clear();
        WebsiteContext.clear();
    }
}
