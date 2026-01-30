package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(title = "分配部门请求", description = "用于给用户分配部门")
public class DispatchDepartmentRequest extends IdRequest {
    @Schema(title = "分配的部门ID列表", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long departmentId;
}
