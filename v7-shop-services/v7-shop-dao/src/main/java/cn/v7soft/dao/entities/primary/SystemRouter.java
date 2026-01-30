package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseTenantEntity;
import cn.v7soft.dao.entities.meta.Meta;
import cn.v7soft.dao.enums.RouterPlatform;
import cn.v7soft.dao.enums.SystemRouterType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_system_routers")
public class SystemRouter extends BaseTenantEntity {
    /**
     * 路由路径
     */
    @Column(nullable = false)
    private String path;

    /**
     * 路由名称
     */
    private String name;

    /**
     * 组件路径或标识，用于动态加载组件
     */
    private String component;

    /**
     * 路由的元数据，如标题和图标
     */
    @Embedded
    private Meta meta;

    /**
     * 路由所属平台
     */
    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private RouterPlatform platform;

    /**
     * 路由类型
     */
    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private SystemRouterType type;

    /**
     * 用于排序的属性，决定菜单项在同级菜单中的顺序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 父路由项
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private SystemRouter parent;

    /**
     * 子路由项
     */
    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") // 使用@OrderBy注解来指定子菜单的默认排序
    private List<SystemRouter>  children = new ArrayList<>();
}
