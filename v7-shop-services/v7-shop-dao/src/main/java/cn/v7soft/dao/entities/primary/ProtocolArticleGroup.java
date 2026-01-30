package cn.v7soft.dao.entities.primary;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;

import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 底部协议分组排序
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@SQLRestriction("status <> 'DELETED'")
@Table(name = "t_protocol_groups")
public class ProtocolArticleGroup extends BaseDataRangeEntity {
    /**
     * 分组名
     */
    private String name;

    /**
     * 分组排序
     */
    private int sort;

    /**
     * 文章语言
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", referencedColumnName = "id", nullable = false)
    private Language language;

    /**
     * 协议分组配置的文章列表
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "t_protocol_group_articles",
            joinColumns = @JoinColumn(name = "protocol_group_id"),
            inverseJoinColumns = @JoinColumn(name = "article_id"))
    private List<Article> articleList = new ArrayList<>();

    /**
     * 文章归属网站，可空
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "website_id", referencedColumnName = "id")
    private Website website;

    /**
     * 文章归属国家，website空的情况下，为当前国家的默认配置
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", referencedColumnName = "id")
    private Country country;

    /**
     * 对应的协议翻译
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_translation_id", referencedColumnName = "id")
    private ProtocolTranslation  translation;
}
