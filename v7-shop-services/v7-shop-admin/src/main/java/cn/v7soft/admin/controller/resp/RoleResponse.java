package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.enums.SystemUserType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@Schema(description = "角色信息响应")
public class RoleResponse extends IdResponse {
    @Schema(title = "角色名称", example = "管理员")
    private String name;

    @Schema(title = "角色描述", example = "管理系统所有设置和用户")
    private String description;

    @Schema(title = "是否跨部门管理")
    private Boolean isCrossDepartment;

    @Builder.Default
    @Schema(title = "跨部门管理的部门ID列表")
    private List<Long> manageDepartmentIds = new ArrayList<>();

    @Builder.Default
    @Schema(title = "角色路由列表", example = "[]")
    private List<Long> systemRouterIds = new ArrayList<>();

    @Schema(title = "角色类型", example = "EMPLOYEE")
    private SystemUserType systemUserType;
}
