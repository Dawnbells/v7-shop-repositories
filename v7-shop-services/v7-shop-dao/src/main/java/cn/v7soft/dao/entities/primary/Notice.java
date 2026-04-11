package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_notices", indexes = {
        @Index(name = "idx_notice_user_id", columnList = "user_id"),
        @Index(name = "idx_notice_is_read", columnList = "is_read"),
        @Index(name = "idx_notice_create_time", columnList = "create_time"),
})
public class Notice extends BaseTenantEntity {

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String content;

    /**
     * 通知类型：SYSTEM（系统通知）、ORDER（订单通知）等，可扩展
     */
    @Column(nullable = false, length = 32)
    private String type;

    /**
     * 目标用户ID，为 null 表示全局通知（所有人可见）
     */
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Builder.Default
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
