package cn.v7soft.admin.configurer;

import cn.v7soft.admin.interceptors.FrontAgentInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册前端机 agent 接口的鉴权/租户拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class FrontAgentConfigurer implements WebMvcConfigurer {

    private final FrontAgentInterceptor frontAgentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order=100：必须晚于 CompanyTenantInterceptor（默认 order=0）执行，
        // 否则其 preHandle 开头的 TenantContext.clear() 会清掉本拦截器设置的租户
        registry.addInterceptor(frontAgentInterceptor)
                .addPathPatterns("/front-agent/**")
                .order(100);
    }
}
