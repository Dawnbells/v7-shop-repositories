package cn.v7soft.dao.entities.primary;

import cn.hutool.json.JSONObject;
import cn.v7soft.dao.converter.JSONConverter;
import cn.v7soft.dao.entities.base.BaseAutoIdDataRangeEntity;
import cn.v7soft.dao.entities.base.BaseDataRangeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 基于系统内置主题模板进行自定义的网站主题
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "t_theme_customs")
public class ThemeCustom extends BaseAutoIdDataRangeEntity {
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "description", length = 1024)
    private String description;

    // 基础配置 存储为 JSON 字符串
    @Column(name = "base_config", columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject baseConfig;

    // 模板配置，存储为 JSON 字符串
    @Column(name = "template_config", columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject templateConfig;

    // i18n配置，存储为 JSON 字符串
    @Column(name = "i18n_config", columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject i18nConfig;

    // 主题配置，存储为 JSON 字符串
    @Column(name = "theme_config", columnDefinition = "JSON")
    @Convert(converter = JSONConverter.class)
    private JSONObject themeConfig;
}
