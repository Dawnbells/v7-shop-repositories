package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.ShareType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑主题模板请求
 */
@Getter
@Setter
@Schema(description = "编辑主题模板请求")
public class EditThemeTemplateRequest extends IdRequest {
    
    @NotBlank(message = "模板名称不能为空")
    @Schema(title = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    
    @Schema(title = "模板描述")
    private String description;
    
    @Schema(title = "封面图URL")
    private String coverImage;
    
    @Schema(title = "共享类型", example = "PRIVATE")
    private ShareType shareType;
    
    @Schema(title = "复制来源模板ID（从模板复制时使用）")
    private Long copyFromId;
}
