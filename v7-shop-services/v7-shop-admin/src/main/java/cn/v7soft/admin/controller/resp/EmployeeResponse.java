package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.enums.Gender;
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
@Schema(description = "分页查询员工响应")
public class EmployeeResponse extends IdResponse {
    @Schema(title = "姓名", example = "John Doe")
    private String name;

    @Schema(title = "性别", example = "MALE")
    private Gender gender;

    @Schema(title = "电话号码", example = "1234567890")
    private String telephone;

    @Schema(title = "密码", example = "1234567890")
    private String password;

    @Schema(title = "所在部门", example = "一部")
    private DepartmentResponse department;

    @Builder.Default
    @Schema(title = "角色列表", example = "1")
    private List<RoleResponse> roles = new ArrayList<>();

    @Schema(title = "每月AI额度", description = "null/0=禁用, -1=无限制, >0=月度额度")
    private Integer monthlyAiCredits;

    @Schema(title = "当月已用AI额度")
    private Integer usedAiCredits;

    @Schema(title = "当月冻结中AI额度")
    private Integer frozenAiCredits;
}
