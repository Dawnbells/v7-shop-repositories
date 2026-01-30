package cn.v7soft.dao.resolver;

import cn.v7soft.dao.tenant.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 公司多租户处理器
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {
    @Override
    public Long resolveCurrentTenantIdentifier() {
        return Optional.ofNullable(TenantContext.getCurrentTenant()).orElse(-1L);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return tenantId == null || tenantId == -1 || TenantContext.isSilent();
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
