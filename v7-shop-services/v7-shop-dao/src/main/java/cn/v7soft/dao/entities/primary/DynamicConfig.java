package cn.v7soft.dao.entities.primary;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.converter.JSONConverter;
import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 动态配置实体，支持公司级别和部门级别的配置
 * departmentId 为 null 时表示公司级别配置
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_dynamic_configs", indexes = {
        @Index(name = "idx_config_name", columnList = "config_name"),
        @Index(name = "idx_department_id", columnList = "department_id"),
        @Index(name = "idx_company_id", columnList = "company_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_config_dept", columnNames = {"config_name", "department_id", "company_id"})
})
public class DynamicConfig extends BaseTenantEntity {

    /**
     * 配置名称，如 "department", "email-settings" 等
     */
    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    /**
     * 部门ID，为 null 时表示公司级别配置
     */
    @Column(name = "department_id")
    private Long departmentId;

    /**
     * 配置值，存储为 JSON
     */
    @Column(name = "config_value", columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject configValue;
}

