package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.Gender;
import cn.v7soft.dao.enums.SystemUserType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示系统用户，管理人员，包括管理员和员工
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_system_users", indexes = {
        @Index(name = "idx_name", columnList = "name"),
        @Index(name = "idx_telephone", columnList = "telephone"),
        @Index(name = "idx_department_id", columnList = "department_id"),
        @Index(name = "idx_user_type", columnList = "user_type"),
        @Index(name = "idx_status", columnList = "status")
})
public class SystemUser extends BaseDataRangeEntity {
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "plain_password", nullable = false)
    private String plainPassword;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "telephone", nullable = false)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private SystemUserType userType;

    /**
     * 角色列表
     */
    @JsonIgnore
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private List<Role> roles = new ArrayList<>();

    /**
     * Department to which this user belongs. A user can belong to only one department.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * 个人商品库
     */
    @OneToOne
    @JoinColumn(name = "product_library_id")
    private ProductLibrary productLibrary;
}
