package cn.v7soft.admin.service;

import cn.v7soft.common.service.IBaseDataRangeService;
import cn.v7soft.dao.entities.primary.ThemeTemplate;

import java.util.List;

public interface IThemeTemplateService extends IBaseDataRangeService<ThemeTemplate> {

    /**
     * 从已有模板复制创建新模板
     *
     * @param sourceId 源模板ID
     * @param name 新模板名称
     * @return 新创建的模板
     */
    ThemeTemplate copyFromTemplate(Long sourceId, String name);

    /**
     * 查询用于远程选择的模板列表
     *
     * @param keyword 搜索关键词
     * @return 模板列表
     */
    List<ThemeTemplate> remoteQuery(String keyword);

    /**
     * 更新模板的主题配置
     *
     * @param id 模板ID
     * @param themeConfig 主题配置JSON
     * @param variableSchema 变量结构JSON
     * @param siteConfig 站点配置JSON
     * @param variableValues 变量值JSON
     */
    void updateThemeConfig(Long id, String themeConfig, String variableSchema, String siteConfig, String variableValues);
}
