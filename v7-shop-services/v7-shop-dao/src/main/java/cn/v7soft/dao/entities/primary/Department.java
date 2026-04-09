package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@BatchSize(size = 50)
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_department")
public class Department extends BaseDataRangeEntity {
    /**
     * 部门名称
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 部门描述
     */
    @Column(length = 100)
    private String description;

    /**
     * 用于排序的属性，决定菜单项在同级部门中的顺序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * 父路由项
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    /**
     * 子路由项
     */
    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC") // 使用@OrderBy注解来指定子菜单的默认排序
    private List<Department> children = new ArrayList<>();
}
