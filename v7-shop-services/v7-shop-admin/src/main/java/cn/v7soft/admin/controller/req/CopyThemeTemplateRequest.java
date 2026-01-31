package cn.v7soft.admin.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 复制主题模板请求
 */
@Getter
@Setter
@Schema(description = "复制主题模板请求")
public class CopyThemeTemplateRequest {
    
    @NotNull(message = "源模板ID不能为空")
    @Schema(title = "源模板ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceId;
    
    @NotBlank(message = "新模板名称不能为空")
    @Schema(title = "新模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
