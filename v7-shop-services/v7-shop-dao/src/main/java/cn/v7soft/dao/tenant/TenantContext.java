package cn.v7soft.dao.tenant;

import java.util.Objects;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.dev33.satoken.stp.StpUtil;
import cn.v7soft.dao.entities.primary.Company;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TenantContext {

    private static final ThreadLocal<Company> currentTenantEntity = new ThreadLocal<>();
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> disableTemporaries = new ThreadLocal<>();

    public static void silent() {
        disableTemporaries.set(Boolean.TRUE);
    }

    public static void restore() {
        disableTemporaries.set(null);
    }

    public static boolean isSilent() {
        return Objects.equals(disableTemporaries.get(), Boolean.TRUE);
    }

    public static void setCurrentTenant(Long tenantId, Company entity) {
//        log.debug("Setting tenant to " + tenantId);
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (servletRequestAttributes != null) {
            // 只有Web上下文环境才能续期
            if (StpUtil.isLogin()) {
                StpUtil.getLoginId(); // renew
            }
        }
        currentTenant.set(tenantId);
        currentTenantEntity.set(entity);
        restore();
    }

    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static String getCurrentTenantStr() {
        return String.valueOf(currentTenant.get());
    }

    public static Company getCurrentTenantEntity() {
        return currentTenantEntity.get();
    }

    public static void clear() {
        currentTenant.remove();
        currentTenantEntity.remove();
        disableTemporaries.remove();
    }

    public static String getImageBaseUrl() {
        return "https://image." + getCurrentTenantEntity().getDomain();
    }

    public static String getCdnImageBaseUrl() {
        return getCurrentTenantEntity().getImageBaseUrl();
    }
}