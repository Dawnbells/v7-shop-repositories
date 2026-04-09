package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.SystemUserType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色实体类，代表系统内的安全角色，
 * 可以分配一组权限。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_roles")
public class Role extends BaseDataRangeEntity {

    /**
     * 角色名称。
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 角色描述。
     */
    @Column(length = 100)
    private String description;

    /**
     * 是否跨部门管理
     */
    @Column(name = "is_cross_department")
    private Boolean isCrossDepartment;

    /**
     * 跨部门管理的部门列表
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_role_departments",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private List<Department> manageDepartments = new ArrayList<>();

    /**
     * 与此角色相关联的权限集合。
     * 使用Set来防止重复。
     */
    @Builder.Default
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_role_routers",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "router_id")
    )
    private List<SystemRouter> systemRouterList = new ArrayList<>();


    /**
     * 用户数据权限范围，
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private SystemUserType userType;

    /**
     * 可以授权给别人的角色列表
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_assignable_roles",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "assignable_role_id"))
    private List<Role> assignableRoles = new ArrayList<>();
}
