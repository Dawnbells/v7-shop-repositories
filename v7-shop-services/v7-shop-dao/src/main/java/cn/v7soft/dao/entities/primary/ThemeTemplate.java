package cn.v7soft.dao.entities.primary;

import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.enums.ShareType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 主题模板实体
 * 支持公司级别 / 个人级别 / 部门共享
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_theme_templates")
public class ThemeTemplate extends BaseAutoIdDataRangeEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /**
     * 主题配置（页面布局、组件、样式）
     */
    @Column(name = "theme_config", columnDefinition = "JSON")
    private String themeConfig;

    /**
     * 变量定义结构（仅编辑器使用）
     */
    @Column(name = "variable_schema", columnDefinition = "JSON")
    private String variableSchema;

    /**
     * 站点配置值
     */
    @Column(name = "site_config", columnDefinition = "JSON")
    private String siteConfig;

    /**
     * 变量实际值
     */
    @Column(name = "variable_values", columnDefinition = "JSON")
    private String variableValues;

    /**
     * 共享类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", length = 20)
    private ShareType shareType;

    /**
     * 复制来源模板
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_from_id")
    private ThemeTemplate sharedFrom;
}
