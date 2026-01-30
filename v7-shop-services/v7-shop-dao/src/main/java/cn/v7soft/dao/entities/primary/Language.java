package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_languages", indexes = {
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_cname", columnList = "cname"),
        @Index(name = "idx_status", columnList = "status")
})
public class Language extends BaseTenantEntity {
    /**
     * 语言名称
     */
    @Column(name = "name", nullable = false, length = 125)
    private String name;

    /**
     * 语言中文名称
     */
    @Column(name = "cname", nullable = false, length = 125)
    private String cname;

    /**
     * 语言代码, ISO-639-1
     */
    @Column(name = "code", nullable = false, length = 32)
    private String code;
}
