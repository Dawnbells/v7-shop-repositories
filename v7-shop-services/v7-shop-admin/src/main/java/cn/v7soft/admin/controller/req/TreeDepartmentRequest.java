package cn.v7soft.admin.controller.req;

import cn.v7soft.core.enums.StatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TreeDepartmentRequest {
    @Schema(title = "查询状态", example = "VALID", requiredMode = Schema.RequiredMode.REQUIRED)
    private StatusEnum status;

    @Schema(title = "是否只查私域部门", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isPrivateDomain;

    @Schema(title = "是否来自员工管理（跨部门需同时具有管理员工权限）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean forEmployeeManagement;
}
