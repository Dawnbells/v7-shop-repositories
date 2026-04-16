package cn.v7soft.core.entities;

import cn.v7soft.core.annotation.V7IdSequence;
import cn.v7soft.core.enums.StatusEnum;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Getter
@Setter
@SuperBuilder
@MappedSuperclass
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
public abstract class BaseEntity implements IBaseEntity {
    /**
     * 自增ID
     */
    @Id
    @V7IdSequence
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

}
