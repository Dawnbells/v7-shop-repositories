package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import cn.v7soft.dao.enums.ArticleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * 文章实体类，代表一篇文章。
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_articles")
public class Article extends BaseDataRangeEntity {
    /**
     * 作为协议时底部显示的名称
     */
    @Column(name = "name")
    private String name;
    /**
     * 文章标题
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * 文章内容
     */
    @Column(name = "content", columnDefinition = "longtext")
    private String content;

    /**
     * 文章类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "article_type", nullable = false, length = 50)
    private ArticleType articleType;

    /**
     * 文章描述
     */
    @Column(name = "description", nullable = false, length = 255)
    private String description;

    /**
     * 文章语言
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", referencedColumnName = "id", nullable = false)
    private Language language;
}
