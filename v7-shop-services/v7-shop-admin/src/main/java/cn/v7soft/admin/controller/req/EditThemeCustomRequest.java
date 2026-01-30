package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑自定义主题的请求
 */
@Getter
@Setter
public class EditThemeCustomRequest extends IdRequest {
    @NotBlank(message = "名称不能为空")
    @Schema(title = "主题名称", example = "自定义主题1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "主题标识不能为空")
    @Schema(title = "主题模板标识", example = "default-light", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateName;

    @Schema(title = "描述", example = "这是一个基于系统模板的自定义主题")
    private String description;
}

