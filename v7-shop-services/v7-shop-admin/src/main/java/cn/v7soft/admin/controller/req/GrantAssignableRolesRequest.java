package cn.v7soft.admin.controller.req;

import cn.v7soft.core.controller.request.IdRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GrantAssignableRolesRequest extends IdRequest {
    @Schema(title = "可分配的角色ID列表", example = "[1, 2]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> assignableRoleIds;
}
