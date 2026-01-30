package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@Schema(description = "部门信息响应")
public class DepartmentResponse extends IdResponse {
    @Schema(title = "部门名称", example = "运营部")
    private String name;

    @Schema(title = "部门描述", example = "运营部门")
    private String description;

    @Schema(title = "上级部门ID", example = "1")
    private Long parentId;

    @Schema(title = "下级ID列表", example = "[]")
    private List<DepartmentResponse> children;

    /**
     * 是否可选
     */
    @Schema(title = "是否禁用", example = "true")
    private boolean disabled;

    public static DepartmentResponse convertEntity(Department department) {
        return filling(department, DepartmentResponse.builder()
                .name(department.getName())
                .description(department.getDescription())
                .disabled(false)
                .build());
    }
}
