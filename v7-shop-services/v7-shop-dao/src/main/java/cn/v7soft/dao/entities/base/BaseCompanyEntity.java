package cn.v7soft.dao.entities.base;

import cn.v7soft.core.entities.BaseEntity;
import cn.v7soft.dao.entities.primary.Company;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
public class BaseCompanyEntity extends BaseEntity {
    /**
     * 角色所属的公司。
     */
    @Builder.Default
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company = TenantContext.getCurrentTenantEntity();
}
