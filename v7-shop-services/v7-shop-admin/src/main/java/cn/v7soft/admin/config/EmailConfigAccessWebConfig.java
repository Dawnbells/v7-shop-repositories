package cn.v7soft.admin.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.dao.utils.SaSessionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class EmailConfigAccessWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new EmailConfigValueAccessInterceptor())
                .addPathPatterns("/config-center/email/value");
    }

    private static class EmailConfigValueAccessInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            // Let CORS preflight and the endpoint's @SaCheckLogin handle unauthenticated requests.
            if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !StpUtil.isLogin()) {
                return true;
            }
            SystemUserDto user = SaSessionUtil.getLoginUser();
            if (user == null || user.isAdmin()) {
                return true;
            }
            String departmentId = request.getParameter("departmentId");
            if (departmentId == null || !departmentId.equals(String.valueOf(user.getDepartmentId()))) {
                throw new IllegalArgumentException("无权读取公司或其他部门的邮件配置");
            }
            return true;
        }
    }
}
