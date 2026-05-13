package cn.v7soft.admin.configurer;

import cn.v7soft.admin.interceptors.CompanyTenantInterceptor;
import cn.v7soft.admin.service.ICompanyService;
import cn.v7soft.dao.repositories.primary.WebsiteRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Order(2)
public class CompanyTenantConfigurer implements WebMvcConfigurer {
    private final ICompanyService companyService;
    private final WebsiteRepository websiteRepository;

    public CompanyTenantConfigurer(ICompanyService companyService, WebsiteRepository websiteRepository) {
        this.companyService = companyService;
        this.websiteRepository = websiteRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new CompanyTenantInterceptor(companyService, websiteRepository)).addPathPatterns("/**");
    }
}
