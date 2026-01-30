package cn.v7soft.dao.entities.base;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.TenantId;

@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
public class BaseTenantEntity extends BaseEntity {
    /**
     * 角色所属的公司。
     */
    @TenantId
    @Builder.Default
    @Column(name = "company_id")
    private Long companyId = TenantContext.getCurrentTenant();
}
