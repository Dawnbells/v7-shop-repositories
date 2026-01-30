package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹实体类。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(
        name = "t_folders",
        indexes = {
                @Index(name = "idx_id", columnList = "id"),
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_company_id", columnList = "company_id"),
        }
)
public class Folder extends BaseDataRangeEntity {
    /**
     * 文件夹名称
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 是否是敏感路径
     */
    @Column(name = "is_sensitive", nullable = false)
    private boolean isSensitive;

    /**
     * 上级文件夹
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parent;

    /**
     * 子文件夹
     */
    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> children = new ArrayList<>();
}
