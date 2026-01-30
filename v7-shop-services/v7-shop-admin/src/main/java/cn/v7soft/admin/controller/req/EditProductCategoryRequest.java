package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProductCategoryRequest extends IdRequest {
    @NotBlank(message = "分类名称不能为空")
    @Schema(title = "分类名称", example = "Electronics")
    private String name;

    @Schema(title = "分类描述", example = "电子产品")
    @Size(max = 255, message = "分类描述最多255个字符")
    private String description;
}
