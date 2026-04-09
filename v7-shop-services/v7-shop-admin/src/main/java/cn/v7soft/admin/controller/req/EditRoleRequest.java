package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import cn.v7soft.dao.enums.SystemUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EditRoleRequest extends IdRequest {
    @NotBlank(message = "角色名称不能为空")
    @Schema(title = "角色名称", example = "管理员", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(title = "是否跨部门管理", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isCrossDepartment;

    @Schema(title = "跨部门管理的部门ID列表", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<Long> manageDepartmentIds;

    @Schema(title = "是否可管理跨部门员工", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isManageEmployee;

    @NotBlank(message = "角色描述不能为空")
    @Schema(title = "角色描述", example = "负责系统的日常管理工作", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(title = "用户类型", example = "EMPLOYEE", requiredMode = Schema.RequiredMode.REQUIRED)
    private SystemUserType systemUserType;
}
