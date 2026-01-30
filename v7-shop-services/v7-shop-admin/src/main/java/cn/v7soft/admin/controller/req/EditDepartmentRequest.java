package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditDepartmentRequest extends IdRequest {
    @NotBlank(message = "部门名称不能为空")
    @Schema(title = "部门名称", example = "一部")
    private String name;
    @NotBlank(message = "部门描述不能为空")
    @Schema(title = "部门描述", example = "部门：一部")
    private String description;
    @Schema(title = "父级部门名称", example = "1")
    private Long parentId;
}
