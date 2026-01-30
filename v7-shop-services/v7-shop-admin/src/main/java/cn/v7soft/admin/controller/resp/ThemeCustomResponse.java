package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.ThemeCustom;
import cn.v7soft.dao.entities.primary.SystemUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "自定义主题响应")
public class ThemeCustomResponse extends IdResponse {
    @Schema(title = "名称")
    private String name;

    @Schema(title = "主题模板名称")
    private String templateName;

    @Schema(title = "描述")
    private String description;

    @Schema(title = "基础配置，JSON 字符串")
    private String baseConfig;

    @Schema(title = "模板信息配置，JSON 字符串")
    private String templateConfig;

    @Schema(title = "i18n 配置，JSON 字符串")
    private String i18nConfig;

    @Schema(title = "主题配置，JSON 字符串")
    private String themeConfig;

    @Schema(title = "归属人名字")
    private String ownerName;

    @Schema(title = "归属人部门")
    private String ownerDepartment;

    public static ThemeCustomResponse convertEntity(ThemeCustom entity) {
        if(entity == null) {
            return null;
        }
        SystemUser owner = entity.getOwner();
        String ownerName = "";
        String ownerDepartment = "";
        if (owner != null) {
            ownerName = owner.getName();
            if (owner.getDepartment() != null) {
                ownerDepartment = owner.getDepartment().getName();
            }
        }
        return ThemeCustomResponse.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .templateName(entity.getTemplateName())
                .description(entity.getDescription())
                .baseConfig(entity.getBaseConfig().toString())
                .templateConfig(entity.getTemplateConfig().toString())
                .i18nConfig(entity.getI18nConfig().toString())
                .themeConfig(entity.getThemeConfig().toString())
                .ownerName(ownerName)
                .ownerDepartment(ownerDepartment)
                .build();
    }
}

