package cn.v7soft.dao.entities.base;

import cn.v7soft.core.entities.IBaseEntity;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
public abstract class BaseCommonEntity implements IBaseEntity, IBaseDataRangeEntity {
    /**
     * 自增ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /**
     * 实体类状态
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEnum status = StatusEnum.VALID;
    /**
     * 实体类创建时间
     */
    @CreationTimestamp
    @Column(updatable = false, nullable = false, name = "create_time")
    private LocalDateTime createTime;
    /**
     * 实体类更新时间
     */
    @UpdateTimestamp
    @Column(nullable = false, name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 角色所属的公司。
     */
    @TenantId
    @Builder.Default
    @Column(name = "company_id")
    private Long companyId = TenantContext.getCurrentTenant();
}
