package cn.v7soft.dao.entities.primary;

import cn.v7soft.core.entities.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 不文明用语实体类，代表特定国家的不文明语言。
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_uncivilized_language")
@SQLRestriction("status <> 'DELETED'")
public class UncivilizedLanguage extends BaseEntity {

    /**
     * 不文明语言文本
     */
    @Column(name = "text", nullable = false, length = 500)
    private String text;

    /**
     * 语言
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", referencedColumnName = "id", nullable = false)
    private Language language;
}
