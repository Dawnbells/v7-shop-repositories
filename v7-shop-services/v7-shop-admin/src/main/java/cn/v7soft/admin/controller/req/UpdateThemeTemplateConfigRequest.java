package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新主题模板配置请求
 */
@Getter
@Setter
@Schema(description = "更新主题模板配置请求")
public class UpdateThemeTemplateConfigRequest {
    
    @NotNull(message = "模板ID不能为空")
    @Schema(title = "模板ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    
    @Schema(title = "主题配置JSON")
    private String themeConfig;
    
    @Schema(title = "变量结构JSON")
    private String variableSchema;
    
    @Schema(title = "站点配置JSON")
    private String siteConfig;
    
    @Schema(title = "变量值JSON")
    private String variableValues;
}
